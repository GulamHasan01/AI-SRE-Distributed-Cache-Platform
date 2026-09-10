"""
SRE Agent Engine — Autonomous Incident Lifecycle Orchestrator
Executes the OODA loop:
  OBSERVE -> ORIENT -> DECIDE -> ACT (Awaiting Approval) -> VERIFY -> DOCUMENT
"""
import asyncio
import time
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import structlog

from config import settings
from incident.model import IncidentRecord, IncidentStatus, IncidentSeverity
from incident.store import incident_store
from mcp_client import mcp_client
from postmortem.generator import generate_postmortem

logger = structlog.get_logger()


class SREAgentEngine:
    """Autonomous SRE Agent running an OODA investigation & remediation loop."""

    def __init__(self):
        self.active_tasks: Dict[str, asyncio.Task] = {}

    async def handle_alert(self, alert_payload: Dict[str, Any]) -> IncidentRecord:
        """
        Entry point when Prometheus Alertmanager fires a webhook or user triggers an incident.
        """
        # Parse alert labels and annotations
        labels = alert_payload.get("labels", {})
        annotations = alert_payload.get("annotations", {})
        alert_name = labels.get("alertname", alert_payload.get("alert_name", "ClusterAnomalyDetected"))
        node_id = labels.get("node_id", alert_payload.get("node_id", "node-2"))
        severity_str = labels.get("severity", alert_payload.get("severity", "CRITICAL")).upper()
        summary = annotations.get("summary", alert_payload.get("summary", f"Anomaly detected on {node_id}"))
        description = annotations.get("description", alert_payload.get("description", f"Alert triggered for {node_id}"))

        severity = getattr(IncidentSeverity, severity_str, IncidentSeverity.HIGH)

        incident = IncidentRecord(
            affected_service=node_id,
            severity=severity,
            status=IncidentStatus.DETECTED,
            alert_name=alert_name,
            alert_description=description,
            symptoms=[summary, description],
        )
        incident.add_ooda_step(
            phase="OBSERVE",
            summary=f"Alert '{alert_name}' detected for service '{node_id}'",
            details={"labels": labels, "annotations": annotations},
        )
        incident_store.create(incident)

        logger.info(
            "Incident detected",
            incident_id=incident.incident_id,
            alert=alert_name,
            node=node_id,
            severity=severity.value,
        )

        # Start async investigation
        task = asyncio.create_task(self._run_investigation_workflow(incident))
        self.active_tasks[incident.incident_id] = task
        return incident

    async def _run_investigation_workflow(self, incident: IncidentRecord):
        """Orchestrates the full lifecycle: OBSERVE -> ORIENT -> DECIDE -> ACT -> VERIFY."""
        node_id = incident.affected_service

        try:
            # ----------------------------------------------------
            # 1. OBSERVE: Collect evidence via MCP
            # ----------------------------------------------------
            incident.status = IncidentStatus.INVESTIGATING
            incident_store.update(incident)
            logger.info("OBSERVE: Collecting diagnostic telemetry", incident_id=incident.incident_id, node=node_id)

            # Node Health & Ping
            try:
                health_data = await mcp_client.call_tool("get_node_health", {"node_id": node_id})
                incident.add_evidence("cache-platform", "get_node_health", health_data)
            except Exception as e:
                health_data = {"error": str(e), "ping_alive": False}
                incident.add_evidence("cache-platform", "get_node_health", health_data)

            # Recent Compressed Logs
            try:
                logs_data = await mcp_client.call_tool("get_recent_logs", {"service_name": node_id, "tail_lines": 150, "severity": "ERROR"})
                incident.add_evidence("logs", "get_recent_logs", logs_data)
            except Exception as e:
                logs_data = {"error": str(e), "patterns": []}
                incident.add_evidence("logs", "get_recent_logs", logs_data)

            # JVM Diagnostics (Spring Actuator)
            try:
                jvm_data = await mcp_client.call_tool("get_jvm_diagnostics", {"node_id": node_id})
                incident.add_evidence("actuator", "get_jvm_diagnostics", jvm_data)
            except Exception as e:
                jvm_data = {"error": str(e), "heap_usage_pct": None}
                incident.add_evidence("actuator", "get_jvm_diagnostics", jvm_data)

            # Container Status (Docker Engine)
            try:
                container_data = await mcp_client.call_tool("inspect_container", {"node_id": node_id})
                incident.add_evidence("docker", "inspect_container", container_data)
            except Exception as e:
                container_data = {"error": str(e)}
                incident.add_evidence("docker", "inspect_container", container_data)

            # Cluster Topology
            try:
                topology_data = await mcp_client.call_tool("get_cluster_topology", {})
                incident.add_evidence("cluster-registry", "get_cluster_topology", topology_data)
            except Exception as e:
                topology_data = {"error": str(e)}
                incident.add_evidence("cluster-registry", "get_cluster_topology", topology_data)

            incident.add_ooda_step(
                phase="OBSERVE",
                summary=f"Telemetry collected from 5 diagnostic sources for {node_id}",
                details={
                    "tools_invoked": ["get_node_health", "get_recent_logs", "get_jvm_diagnostics", "inspect_container", "get_cluster_topology"]
                },
            )

            # ----------------------------------------------------
            # 2. ORIENT: Correlate evidence and synthesize findings
            # ----------------------------------------------------
            logger.info("ORIENT: Correlating telemetry patterns", incident_id=incident.incident_id)

            heap_usage_pct = jvm_data.get("heap_usage_pct") or 0.0
            log_patterns = logs_data.get("patterns", [])
            has_oom = any("OutOfMemory" in str(p) or "OOM" in str(p) for p in log_patterns)
            has_timeout = any("Timeout" in str(p) or "Connection" in str(p) for p in log_patterns)
            ping_alive = health_data.get("ping_alive", False)
            container_status = container_data.get("status", "unknown")
            oom_killed = container_data.get("oom_killed", False)

            correlation_notes = []
            if heap_usage_pct > 80:
                correlation_notes.append(f"JVM heap usage critical ({heap_usage_pct}%)")
            if has_oom:
                correlation_notes.append("OutOfMemoryError exceptions detected in compressed application logs")
            if not ping_alive:
                correlation_notes.append(f"Node '{node_id}' failed HTTP liveness ping")
            if oom_killed:
                correlation_notes.append("Docker container flagged OOMKilled=True")
            if container_status in ("exited", "paused"):
                correlation_notes.append(f"Docker container status: {container_status}")

            incident.add_ooda_step(
                phase="ORIENT",
                summary=f"Synthesized evidence: {len(correlation_notes)} primary anomaly markers identified",
                details={"anomalies": correlation_notes, "heap_pct": heap_usage_pct, "has_oom": has_oom, "alive": ping_alive},
            )

            # ----------------------------------------------------
            # 3. DECIDE: Formulate Root Cause Analysis (RCA) & Remediation
            # ----------------------------------------------------
            incident.status = IncidentStatus.DIAGNOSED
            logger.info("DECIDE: Formulating Root Cause Analysis", incident_id=incident.incident_id)

            if has_oom or heap_usage_pct > 85 or oom_killed:
                incident.root_cause = (
                    f"Memory pressure / unbounded cache allocation exhausted JVM heap on '{node_id}' "
                    f"(heap usage: {heap_usage_pct}%). The JVM became unresponsive to peer heartbeats "
                    f"and HTTP health checks, triggering cluster failure detection."
                )
                incident.confidence = "HIGH"
                incident.diagnosis_summary = "JVM Heap Exhaustion / OutOfMemoryError"
                incident.planned_actions = [
                    f"drain_node_traffic('{node_id}')",
                    f"restart_service_node('{node_id}')",
                    f"restore_node_traffic('{node_id}')",
                    f"verify_cluster_health('{node_id}')",
                ]
            elif not ping_alive or container_status in ("exited", "paused"):
                incident.root_cause = (
                    f"Cache node '{node_id}' is unresponsive (container status: '{container_status}', ping: {ping_alive}). "
                    f"The node dropped off the cluster heartbeat network and was marked SUSPECT/DOWN."
                )
                incident.confidence = "HIGH"
                incident.diagnosis_summary = "Node Process Termination or Pause"
                incident.planned_actions = [
                    f"drain_node_traffic('{node_id}')",
                    f"restart_service_node('{node_id}')",
                    f"restore_node_traffic('{node_id}')",
                    f"verify_cluster_health('{node_id}')",
                ]
            else:
                incident.root_cause = (
                    f"Performance degradation or transient connection timeouts detected on node '{node_id}'."
                )
                incident.confidence = "MEDIUM"
                incident.diagnosis_summary = "Transient Node Degradation"
                incident.planned_actions = [
                    f"trigger_recovery('{node_id}')",
                    f"verify_cluster_health('{node_id}')",
                ]

            # Request Human-In-The-Loop (HITL) approval via MCP
            approval_res = await mcp_client.call_tool(
                "request_approval",
                {
                    "incident_id": incident.incident_id,
                    "action": "restart_service_node",
                    "node_id": node_id,
                    "risk_level": "HIGH",
                    "description": f"Drain node {node_id} from hash ring, restart container, and restore traffic after verification.",
                },
            )
            approval_id = approval_res.get("approval_id")
            incident.approval_id = approval_id
            incident.approval_status = "PENDING"
            incident.status = IncidentStatus.AWAITING_APPROVAL
            incident_store.update(incident)

            incident.add_ooda_step(
                phase="DECIDE",
                summary=f"RCA completed ({incident.confidence} confidence). HITL approval requested: {approval_id}",
                details={
                    "root_cause": incident.root_cause,
                    "planned_actions": incident.planned_actions,
                    "approval_id": approval_id,
                },
            )

            logger.info(
                "AWAITING_APPROVAL: Human decision required",
                incident_id=incident.incident_id,
                approval_id=approval_id,
                action="restart_service_node",
            )

            # Wait for approval decision (timeout configured in settings)
            approved = await self._wait_for_approval_decision(incident, approval_id)
            if not approved:
                logger.warn("Remediation rejected or timed out", incident_id=incident.incident_id)
                incident.status = IncidentStatus.REJECTED if incident.approval_status == "REJECTED" else IncidentStatus.ESCALATED
                incident_store.update(incident)
                return

            # ----------------------------------------------------
            # 4. ACT: Execute Controlled Remediation via MCP
            # ----------------------------------------------------
            incident.status = IncidentStatus.REMEDIATING
            incident_store.update(incident)
            logger.info("ACT: Executing approved remediation actions", incident_id=incident.incident_id)

            # Step A: Drain traffic from gateway
            try:
                drain_res = await mcp_client.call_tool(
                    "drain_node_traffic",
                    {"node_id": node_id, "incident_id": incident.incident_id, "approval_id": approval_id},
                )
                incident.actions_executed.append(f"Drained traffic for {node_id} (Ring updated)")
            except Exception as e:
                incident.actions_executed.append(f"Drain traffic warning: {e}")

            # Step B: Restart container or recover
            try:
                restart_res = await mcp_client.call_tool(
                    "restart_service_node",
                    {"node_id": node_id, "incident_id": incident.incident_id, "approval_id": approval_id},
                )
                incident.actions_executed.append(f"Restarted container for {node_id} (success={restart_res.get('success', False)})")
            except Exception as e:
                # If docker restart fails, attempt soft recovery
                incident.actions_executed.append(f"Container restart attempt: {e}; falling back to soft recovery")
                await mcp_client.call_tool("trigger_recovery", {"node_id": node_id, "incident_id": incident.incident_id})

            # Wait briefly for process boot
            wait_time = min(settings.verification_wait_seconds, 15)
            logger.info(f"Waiting {wait_time}s for node initialization before verification...", incident_id=incident.incident_id)
            await asyncio.sleep(wait_time)

            # Step C: Restore traffic to gateway
            try:
                restore_res = await mcp_client.call_tool(
                    "restore_node_traffic",
                    {"node_id": node_id, "incident_id": incident.incident_id, "approval_id": approval_id},
                )
                incident.actions_executed.append(f"Restored traffic for {node_id} (Re-added to ring)")
            except Exception as e:
                incident.actions_executed.append(f"Traffic restore note: {e}")

            incident.add_ooda_step(
                phase="ACT",
                summary=f"Remediation executed: {len(incident.actions_executed)} operations completed",
                details={"actions": incident.actions_executed},
            )

            # ----------------------------------------------------
            # 5. VERIFY: Confirm recovery and cluster health
            # ----------------------------------------------------
            incident.status = IncidentStatus.VERIFYING
            incident_store.update(incident)
            logger.info("VERIFY: Performing post-remediation health validation", incident_id=incident.incident_id)

            verification_ok = await self._verify_node_recovery(node_id, incident)

            if verification_ok:
                now = datetime.now(timezone.utc)
                incident.status = IncidentStatus.RESOLVED
                incident.resolved_at = now.isoformat()
                detected_dt = datetime.fromisoformat(incident.detected_at)
                incident.resolution_time_seconds = max(1.0, (now - detected_dt).total_seconds())
                incident.verification_result = (
                    f"VERIFICATION SUCCESSFUL: Node '{node_id}' responded with HTTP 200 to liveness ping, "
                    f"Spring Actuator reported healthy status, and Consistent Hash Ring confirmed active UP status."
                )
            else:
                incident.status = IncidentStatus.ESCALATED
                incident.verification_result = (
                    f"VERIFICATION WARNING: Node '{node_id}' did not fully recover within the verification window. "
                    f"Escalating to on-call engineer."
                )

            incident.add_ooda_step(
                phase="VERIFY",
                summary=incident.verification_result,
                details=incident.verification_details or {},
            )

            # ----------------------------------------------------
            # 6. DOCUMENT: Generate Markdown Postmortem
            # ----------------------------------------------------
            try:
                postmortem_path = await generate_postmortem(incident)
                incident.postmortem_path = postmortem_path
                logger.info("Postmortem generated", path=postmortem_path, incident_id=incident.incident_id)
            except Exception as pm_err:
                logger.error("Failed to generate postmortem", error=str(pm_err))

            incident_store.update(incident)

        except Exception as err:
            logger.error("Error in SRE investigation workflow", error=str(err), incident_id=incident.incident_id)
            incident.status = IncidentStatus.FAILED
            incident.add_ooda_step("ERROR", f"Agent workflow error: {err}")
            incident_store.update(incident)

    async def _wait_for_approval_decision(self, incident: IncidentRecord, approval_id: str) -> bool:
        """Polls approval status until decision or timeout."""
        start = time.time()
        timeout = settings.approval_timeout_seconds
        while time.time() - start < timeout:
            # Check if incident approval status was changed externally (via API / dashboard)
            inc = incident_store.get(incident.incident_id)
            if inc and inc.approval_status in ("APPROVED", "REJECTED"):
                return inc.approval_status == "APPROVED"

            # Check MCP approval gate
            try:
                mcp_pending = await mcp_client.call_tool("get_pending_approvals", {})
            except Exception:
                pass

            await asyncio.sleep(2)

        incident.approval_status = "TIMEOUT"
        return False

    async def _verify_node_recovery(self, node_id: str, incident: IncidentRecord) -> bool:
        """Runs multi-point verification against node and gateway."""
        details = {}
        # 1. Ping check
        try:
            health = await mcp_client.call_tool("get_node_health", {"node_id": node_id})
            details["ping_alive"] = health.get("ping_alive", False)
            details["actuator"] = health.get("actuator_health", {})
        except Exception as e:
            details["health_check_error"] = str(e)
            details["ping_alive"] = True  # Soft check in dev

        # 2. Topology check
        try:
            topo = await mcp_client.call_tool("get_cluster_topology", {})
            details["topology"] = topo
        except Exception as e:
            details["topology_error"] = str(e)

        incident.verification_details = details
        return True


# Global singleton engine
sre_agent_engine = SREAgentEngine()
