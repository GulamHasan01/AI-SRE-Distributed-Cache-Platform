"""
sre-cluster-mcp — Main FastMCP Server Entrypoint
Registers all diagnostic, remediation, and chaos tools.
Runs over STDIO (for direct agent use) or SSE (for remote/dashboard use).

Usage:
    python server.py               # STDIO mode
    python server.py --transport sse --port 8000  # SSE mode
"""
import sys
import asyncio
from fastmcp import FastMCP
from config import settings

# ============================================================
# Import all tool implementations
# ============================================================
from tools.diagnostic import (
    get_recent_logs,
    query_prometheus_metric,
    get_jvm_diagnostics,
    get_thread_dump,
    get_cluster_topology,
    inspect_container,
    get_node_health,
    get_active_prometheus_alerts,
)
from tools.remediation import (
    restart_service_node,
    drain_node_traffic,
    restore_node_traffic,
    evict_cache_node,
    trigger_recovery,
    request_approval,
)
from tools.chaos import (
    simulate_node_failure,
    simulate_node_recovery,
    simulate_high_memory,
)

# ============================================================
# Create MCP Server
# ============================================================
mcp = FastMCP(
    name=settings.mcp_server_name,
    version=settings.mcp_server_version,
    description=(
        "SRE Cluster MCP — Provides AI agents with structured, audited access to the "
        "Distributed Cache Platform for diagnostics, remediation, and controlled chaos injection. "
        "All mutating operations require prior human approval. "
        "The LLM never has raw shell or arbitrary command access."
    ),
)

# ============================================================
# Register Diagnostic Tools (read-only)
# ============================================================
mcp.tool(
    name="get_recent_logs",
    description="Fetch and compress recent logs for a cache node. Extracts error patterns, frequencies, and stack traces.",
)(get_recent_logs)

mcp.tool(
    name="query_prometheus_metric",
    description="Execute a PromQL query against the Prometheus server and return structured metrics.",
)(query_prometheus_metric)

mcp.tool(
    name="get_jvm_diagnostics",
    description="Retrieve JVM diagnostics (heap%, GC pauses, CPU, thread counts) from a cache node via Spring Boot Actuator.",
)(get_jvm_diagnostics)

mcp.tool(
    name="get_thread_dump",
    description="Get a thread dump from a cache node. Useful for detecting deadlocks and thread pool starvation.",
)(get_thread_dump)

mcp.tool(
    name="get_cluster_topology",
    description="Retrieve the current cluster topology: all nodes, their statuses (UP/SUSPECT/DOWN), and heartbeat ages.",
)(get_cluster_topology)

mcp.tool(
    name="inspect_container",
    description="Inspect a cache node's Docker container: CPU%, memory usage, restart count, OOM killed status.",
)(inspect_container)

mcp.tool(
    name="get_node_health",
    description="Get the full health status of a cache node: ping liveness, actuator health, and cluster registry view.",
)(get_node_health)

mcp.tool(
    name="get_active_prometheus_alerts",
    description="Fetch all currently firing Prometheus alerts with severity and description.",
)(get_active_prometheus_alerts)

# ============================================================
# Register Remediation Tools (mutating — require approval)
# ============================================================
mcp.tool(
    name="request_approval",
    description=(
        "Request human approval for a destructive remediation action. "
        "MUST be called before restart_service_node or drain_node_traffic. "
        "Returns an approval_id that must be passed to the remediation tool."
    ),
)(request_approval)

mcp.tool(
    name="restart_service_node",
    description="Restart a cache node container. HIGH RISK. Requires a valid approved approval_id from request_approval.",
)(restart_service_node)

mcp.tool(
    name="drain_node_traffic",
    description="Remove a node from the consistent hash ring (mark DOWN at gateway). Requires approval.",
)(drain_node_traffic)

mcp.tool(
    name="restore_node_traffic",
    description="Re-add a node to the consistent hash ring (mark UP at gateway). Requires approval.",
)(restore_node_traffic)

mcp.tool(
    name="evict_cache_node",
    description="Clear all cache entries on a node (DELETE /api/v1/cache). MEDIUM risk, no approval required.",
)(evict_cache_node)

mcp.tool(
    name="trigger_recovery",
    description="Attempt soft recovery by restoring a node's routing status without restarting the container.",
)(trigger_recovery)

# ============================================================
# Register Chaos Tools (dev-only — gated by CHAOS_ENABLED)
# ============================================================
if settings.chaos_enabled:
    mcp.tool(
        name="simulate_node_failure",
        description="[DEV ONLY] Pause a cache node container to simulate a node failure.",
    )(simulate_node_failure)

    mcp.tool(
        name="simulate_node_recovery",
        description="[DEV ONLY] Unpause a previously paused node container.",
    )(simulate_node_recovery)

    mcp.tool(
        name="simulate_high_memory",
        description="[DEV ONLY] Trigger memory pressure on a node via the chaos endpoint.",
    )(simulate_high_memory)


# ============================================================
# FastAPI HTTP Bridge (for direct REST / agent / dashboard tool invocation)
# ============================================================
import json
import inspect
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Any, Dict, Optional
from security.approval_gate import approval_gate

app = FastAPI(
    title="SRE Cluster MCP API",
    version=settings.mcp_server_version,
    description="HTTP & SSE Bridge for SRE Cluster MCP",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

TOOLS_REGISTRY = {
    "get_recent_logs": {"fn": get_recent_logs, "description": "Fetch and compress recent logs for a cache node.", "mutating": False},
    "query_prometheus_metric": {"fn": query_prometheus_metric, "description": "Execute a PromQL query against Prometheus.", "mutating": False},
    "get_jvm_diagnostics": {"fn": get_jvm_diagnostics, "description": "Retrieve JVM diagnostics from a cache node via Actuator.", "mutating": False},
    "get_thread_dump": {"fn": get_thread_dump, "description": "Get thread dump from a cache node.", "mutating": False},
    "get_cluster_topology": {"fn": get_cluster_topology, "description": "Retrieve current cluster topology and node statuses.", "mutating": False},
    "inspect_container": {"fn": inspect_container, "description": "Inspect cache node Docker container.", "mutating": False},
    "get_node_health": {"fn": get_node_health, "description": "Get health status of a node including ping and Actuator.", "mutating": False},
    "get_active_prometheus_alerts": {"fn": get_active_prometheus_alerts, "description": "Fetch active Prometheus alerts.", "mutating": False},
    "request_approval": {"fn": request_approval, "description": "Request human approval for a destructive action.", "mutating": False},
    "restart_service_node": {"fn": restart_service_node, "description": "Restart a cache node container (requires approval).", "mutating": True},
    "drain_node_traffic": {"fn": drain_node_traffic, "description": "Remove node from hash ring (mark DOWN).", "mutating": True},
    "restore_node_traffic": {"fn": restore_node_traffic, "description": "Re-add node to hash ring (mark UP).", "mutating": True},
    "evict_cache_node": {"fn": evict_cache_node, "description": "Clear all cache entries on a node.", "mutating": True},
    "trigger_recovery": {"fn": trigger_recovery, "description": "Attempt soft recovery by restoring routing status.", "mutating": True},
    "simulate_node_failure": {"fn": simulate_node_failure, "description": "[DEV] Pause a node container.", "mutating": True},
    "simulate_node_recovery": {"fn": simulate_node_recovery, "description": "[DEV] Unpause a node container.", "mutating": True},
    "simulate_high_memory": {"fn": simulate_high_memory, "description": "[DEV] Trigger memory pressure.", "mutating": True},
}

class ToolCallRequest(BaseModel):
    name: str
    arguments: Dict[str, Any] = {}

class ApprovalDecisionRequest(BaseModel):
    approval_id: str
    decision: str
    decided_by: Optional[str] = "human"

@app.get("/health")
async def health_check():
    return {"status": "UP", "server": settings.mcp_server_name, "version": settings.mcp_server_version}

@app.get("/tools/list")
async def list_tools():
    tools = [
        {"name": name, "description": meta["description"], "is_mutating": meta["mutating"]}
        for name, meta in TOOLS_REGISTRY.items()
    ]
    return {"tools": tools}

@app.post("/tools/call")
async def call_tool_endpoint(req: ToolCallRequest):
    meta = TOOLS_REGISTRY.get(req.name)
    if not meta:
        raise HTTPException(status_code=404, detail=f"Tool '{req.name}' not found")
    fn = meta["fn"]
    try:
        if inspect.iscoroutinefunction(fn):
            result = await fn(**req.arguments)
        else:
            result = fn(**req.arguments)
        return {
            "name": req.name,
            "content": [{"type": "text", "text": json.dumps(result)}],
            "result": result
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/approval/pending")
async def get_pending_approvals():
    return {"pending": approval_gate.list_pending()}

@app.post("/approval/decide")
async def decide_approval(req: ApprovalDecisionRequest):
    dec = req.decision.upper()
    if dec not in ("APPROVED", "REJECTED"):
        raise HTTPException(status_code=400, detail="Decision must be APPROVED or REJECTED")
    success = approval_gate.decide(req.approval_id, dec, req.decided_by or "human")
    if not success:
        raise HTTPException(status_code=404, detail=f"Approval request '{req.approval_id}' not found")
    return {"success": True, "approval_id": req.approval_id, "status": dec}


# ============================================================
# Entry point
# ============================================================
if __name__ == "__main__":
    transport = "http"
    port = 8000
    for i, arg in enumerate(sys.argv[1:], 1):
        if arg == "--transport" and i + 1 < len(sys.argv):
            transport = sys.argv[i + 1]
        if arg == "--port" and i + 1 < len(sys.argv):
            port = int(sys.argv[i + 1])

    if transport == "stdio":
        mcp.run(transport="stdio")
    elif transport == "fastmcp-sse":
        mcp.run(transport="sse", port=port)
    else:
        import uvicorn
        uvicorn.run(app, host="0.0.0.0", port=port)

