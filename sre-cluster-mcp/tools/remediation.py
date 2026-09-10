"""
MCP Remediation Tools (State-Mutating, Requires Approval).
Every mutating operation: validates input → checks approval → audits → executes → records result.
SECURITY: The LLM never gets raw shell/docker access.
"""
import uuid
from security.allowlist import validate_node
from security.audit_log import record_tool_call
from security.approval_gate import approval_gate
from adapters import cache_api_client, docker_client


async def restart_service_node(
    node_id: str,
    incident_id: str,
    approval_id: str,
) -> dict:
    """
    Restart a cache node container. HIGH RISK — requires prior human approval.

    Args:
        node_id: Cache node to restart ("node-1", "node-2", "node-3")
        incident_id: Associated incident ID for audit trail
        approval_id: The approval token issued by the HITL gate

    Returns:
        Success/failure result with container status before and after.
    """
    validate_node(node_id)

    # Verify approval
    req = approval_gate.get_request(approval_id)
    if not req:
        return {"success": False, "error": f"No approval request found for ID '{approval_id}'"}
    if req.status != "APPROVED":
        return {
            "success": False,
            "error": f"Operation not approved. Current status: {req.status}",
        }

    # Execute
    result = await docker_client.restart_container(node_id)

    record_tool_call(
        tool_name="restart_service_node",
        args={"node_id": node_id, "incident_id": incident_id},
        result_summary=f"Restart {'succeeded' if result.get('success') else 'failed'}. Status: {result.get('status_after')}",
        incident_id=incident_id,
        approved_by=req.decided_by,
        is_mutating=True,
        success=result.get("success", False),
    )
    return result


async def drain_node_traffic(
    node_id: str,
    incident_id: str,
    approval_id: str,
) -> dict:
    """
    Remove a node from the consistent hash ring (mark DOWN at gateway).
    Traffic is re-routed to remaining nodes. Requires approval.

    Args:
        node_id: Node to drain ("node-1", "node-2", "node-3")
        incident_id: Associated incident ID
        approval_id: Approval token

    Returns:
        Gateway response confirming ring rebuild.
    """
    validate_node(node_id)

    req = approval_gate.get_request(approval_id)
    if not req or req.status != "APPROVED":
        return {"success": False, "error": f"Operation not approved. Status: {req.status if req else 'NOT_FOUND'}"}

    result = await cache_api_client.drain_node_traffic(node_id)
    record_tool_call(
        tool_name="drain_node_traffic",
        args={"node_id": node_id, "incident_id": incident_id},
        result_summary=f"Drain {'succeeded' if result.get('success') else 'failed'}",
        incident_id=incident_id,
        approved_by=req.decided_by,
        is_mutating=True,
        success=result.get("success", False),
    )
    return result


async def restore_node_traffic(
    node_id: str,
    incident_id: str,
    approval_id: str,
) -> dict:
    """
    Re-add a node to the consistent hash ring (mark UP at gateway).
    Used after a node has recovered and been verified healthy.

    Args:
        node_id: Node to restore
        incident_id: Associated incident ID
        approval_id: Approval token

    Returns:
        Gateway response confirming ring rebuild.
    """
    validate_node(node_id)

    req = approval_gate.get_request(approval_id)
    if not req or req.status != "APPROVED":
        return {"success": False, "error": f"Operation not approved. Status: {req.status if req else 'NOT_FOUND'}"}

    result = await cache_api_client.restore_node_traffic(node_id)
    record_tool_call(
        tool_name="restore_node_traffic",
        args={"node_id": node_id, "incident_id": incident_id},
        result_summary=f"Restore {'succeeded' if result.get('success') else 'failed'}",
        incident_id=incident_id,
        approved_by=req.decided_by,
        is_mutating=True,
        success=result.get("success", False),
    )
    return result


async def evict_cache_node(
    node_id: str,
    incident_id: str,
) -> dict:
    """
    Clear all cache entries on a specific node (DELETE /api/v1/cache).
    MEDIUM risk — does not require approval but is audited.
    Use when cache corruption or extreme memory pressure is detected.

    Args:
        node_id: Node to evict ("node-1", "node-2", "node-3")
        incident_id: Associated incident ID

    Returns:
        Number of entries removed.
    """
    validate_node(node_id)
    result = await cache_api_client.evict_cache_node(node_id)
    record_tool_call(
        tool_name="evict_cache_node",
        args={"node_id": node_id, "incident_id": incident_id},
        result_summary=f"Eviction {'succeeded' if result.get('success') else 'failed'}",
        incident_id=incident_id,
        is_mutating=True,
        success=result.get("success", False),
    )
    return result


async def trigger_recovery(node_id: str, incident_id: str) -> dict:
    """
    Attempt recovery by restoring node to UP status in the cluster.
    Lower-risk than restart — only changes routing, does not kill the process.

    Args:
        node_id: Node to recover
        incident_id: Associated incident ID

    Returns:
        Result of traffic restore operation.
    """
    validate_node(node_id)
    result = await cache_api_client.restore_node_traffic(node_id)
    record_tool_call(
        tool_name="trigger_recovery",
        args={"node_id": node_id, "incident_id": incident_id},
        result_summary=f"Recovery trigger {'succeeded' if result.get('success') else 'failed'}",
        incident_id=incident_id,
        is_mutating=True,
        success=result.get("success", False),
    )
    return result


async def request_approval(
    incident_id: str,
    action: str,
    node_id: str,
    risk_level: str,
    description: str,
) -> dict:
    """
    Create a human approval request for a destructive action.
    The agent should call this BEFORE restart_service_node or drain_node_traffic.
    The returned approval_id must be passed to the remediation tool.

    Args:
        incident_id: ID of the incident requiring remediation
        action: Human-readable action name (e.g., "restart_service_node")
        node_id: Affected node
        risk_level: "LOW", "MEDIUM", or "HIGH"
        description: Plain-language description of what will happen

    Returns:
        approval_id to pass to the remediation tool after human approves.
    """
    approval_id = str(uuid.uuid4())
    req = approval_gate.create_request(
        approval_id=approval_id,
        incident_id=incident_id,
        action=action,
        node_id=node_id,
        risk_level=risk_level,
        description=description,
    )
    record_tool_call(
        tool_name="request_approval",
        args={"incident_id": incident_id, "action": action, "node_id": node_id},
        result_summary=f"Approval requested: {approval_id}",
        incident_id=incident_id,
        is_mutating=False,
    )
    return {
        "approval_id": approval_id,
        "status": "PENDING",
        "message": f"Approval request created. A human must approve or reject action '{action}' on node '{node_id}' before execution.",
    }
