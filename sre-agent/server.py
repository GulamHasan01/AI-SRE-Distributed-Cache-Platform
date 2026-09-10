"""
SRE Agent API Server — FastAPI Service
Exposes Alertmanager webhook, HITL approval endpoints, incident inspection, and cluster overview.
"""
import sys
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Any, Dict, List, Optional
import structlog

from config import settings
from incident.model import IncidentRecord, IncidentStatus
from incident.store import incident_store
from agent_engine import sre_agent_engine
from mcp_client import mcp_client

logger = structlog.get_logger()

app = FastAPI(
    title="Autonomous AI SRE Agent API",
    version="1.0.0",
    description="Incident management, Prometheus webhook, and HITL decision API",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class AlertmanagerWebhook(BaseModel):
    receiver: Optional[str] = "sre-agent"
    status: Optional[str] = "firing"
    alerts: List[Dict[str, Any]] = []
    groupLabels: Optional[Dict[str, Any]] = {}
    commonLabels: Optional[Dict[str, Any]] = {}
    commonAnnotations: Optional[Dict[str, Any]] = {}


class ManualTriggerRequest(BaseModel):
    node_id: str = "node-2"
    alert_name: str = "ManualHeartbeatFailure"
    severity: str = "HIGH"
    summary: str = "Node heartbeat missed or OOM simulated"
    description: str = "Simulated or manual incident trigger for SRE demonstration"


class HITLDecisionRequest(BaseModel):
    decided_by: Optional[str] = "human_sre"


@app.get("/health")
async def health_check():
    mcp_healthy = await mcp_client.is_healthy()
    return {
        "status": "UP",
        "service": "sre-agent",
        "mcp_connected": mcp_healthy,
        "active_incidents": len(incident_store.list_active()),
    }


@app.post("/api/sre/alerts")
async def receive_prometheus_alert(webhook: AlertmanagerWebhook):
    """
    Receives alerts from Prometheus Alertmanager and dispatches investigation workflows.
    """
    logger.info("Prometheus webhook received", alert_count=len(webhook.alerts))
    dispatched = []

    for alert in webhook.alerts:
        if alert.get("status") == "resolved":
            continue

        incident = await sre_agent_engine.handle_alert(alert)
        dispatched.append(incident.incident_id)

    return {"status": "accepted", "incidents_created": dispatched}


@app.post("/api/sre/incidents/trigger")
async def trigger_incident(req: ManualTriggerRequest):
    """
    Manual/Demo endpoint to trigger incident investigation on a node.
    """
    payload = {
        "labels": {
            "alertname": req.alert_name,
            "node_id": req.node_id,
            "severity": req.severity,
        },
        "annotations": {
            "summary": req.summary,
            "description": req.description,
        },
    }
    incident = await sre_agent_engine.handle_alert(payload)
    return {"status": "investigation_started", "incident": incident.to_dict()}


@app.get("/api/sre/incidents")
async def list_incidents(status: Optional[str] = None):
    """List all incidents, optionally filtered by status."""
    if status:
        try:
            enum_status = IncidentStatus(status.upper())
            incidents = incident_store.list_by_status(enum_status)
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid status: {status}")
    else:
        incidents = incident_store.list_all()

    # Sort reverse chronological
    sorted_incidents = sorted(incidents, key=lambda i: i.detected_at, reverse=True)
    return {"incidents": [i.to_dict() for i in sorted_incidents]}


@app.get("/api/sre/incidents/{incident_id}")
async def get_incident(incident_id: str):
    """Get full details of a specific incident."""
    incident = incident_store.get(incident_id)
    if not incident:
        raise HTTPException(status_code=404, detail="Incident not found")
    return {"incident": incident.to_dict()}


@app.post("/api/sre/incidents/{incident_id}/approve")
async def approve_incident(incident_id: str, req: HITLDecisionRequest = HITLDecisionRequest()):
    """Human approval for remediation action."""
    incident = incident_store.get(incident_id)
    if not incident:
        raise HTTPException(status_code=404, detail="Incident not found")

    if incident.status != IncidentStatus.AWAITING_APPROVAL:
        raise HTTPException(status_code=400, detail=f"Incident is not awaiting approval (status: {incident.status.value})")

    incident.approval_status = "APPROVED"
    incident_store.update(incident)

    # Inform MCP approval gate
    if incident.approval_id:
        try:
            await mcp_client.call_tool(
                "request_approval_decision",  # or direct decide endpoint
                {"approval_id": incident.approval_id, "decision": "APPROVED", "decided_by": req.decided_by},
            )
        except Exception:
            pass

    return {"status": "APPROVED", "incident_id": incident_id, "decided_by": req.decided_by}


@app.post("/api/sre/incidents/{incident_id}/reject")
async def reject_incident(incident_id: str, req: HITLDecisionRequest = HITLDecisionRequest()):
    """Human rejection for remediation action."""
    incident = incident_store.get(incident_id)
    if not incident:
        raise HTTPException(status_code=404, detail="Incident not found")

    incident.approval_status = "REJECTED"
    incident.status = IncidentStatus.REJECTED
    incident_store.update(incident)

    return {"status": "REJECTED", "incident_id": incident_id, "decided_by": req.decided_by}


@app.get("/api/sre/cluster/overview")
async def cluster_overview():
    """Aggregated health overview for dashboard."""
    try:
        topo = await mcp_client.call_tool("get_cluster_topology", {})
    except Exception as e:
        topo = {"error": str(e)}

    active = incident_store.list_active()
    all_incidents = incident_store.list_all()

    return {
        "active_incident_count": len(active),
        "total_incident_count": len(all_incidents),
        "active_incidents": [i.to_dict() for i in active],
        "recent_incidents": [i.to_dict() for i in sorted(all_incidents, key=lambda x: x.detected_at, reverse=True)[:5]],
        "cluster_topology": topo,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=settings.agent_api_port)
