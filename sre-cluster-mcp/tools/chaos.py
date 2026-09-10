"""
Chaos / Failure Injection Tools (Dev-Only).
SECURITY: These tools are completely disabled unless CHAOS_ENABLED=true.
They must NEVER be enabled in production.
"""
from security.allowlist import validate_node, validate_chaos_enabled
from security.audit_log import record_tool_call
from adapters import docker_client


async def simulate_node_failure(node_id: str) -> dict:
    """
    Simulate a node failure by pausing its Docker container.
    The node stops responding to heartbeats and Prometheus scrapes.
    DEV/DEMO ONLY — requires CHAOS_ENABLED=true.

    Args:
        node_id: Node to pause ("node-1", "node-2", "node-3")
    """
    validate_chaos_enabled()
    validate_node(node_id)
    result = await docker_client.pause_container(node_id)
    record_tool_call(
        tool_name="simulate_node_failure",
        args={"node_id": node_id},
        result_summary=f"Node '{node_id}' paused for chaos simulation",
        is_mutating=True,
    )
    return result


async def simulate_node_recovery(node_id: str) -> dict:
    """
    Recover a previously paused node (unpause container).
    DEV/DEMO ONLY — requires CHAOS_ENABLED=true.

    Args:
        node_id: Node to unpause
    """
    validate_chaos_enabled()
    validate_node(node_id)
    result = await docker_client.unpause_container(node_id)
    record_tool_call(
        tool_name="simulate_node_recovery",
        args={"node_id": node_id},
        result_summary=f"Node '{node_id}' unpaused",
        is_mutating=True,
    )
    return result


async def simulate_high_memory(node_id: str, target_mb: int = 512) -> dict:
    """
    Trigger memory pressure on a node via the chaos HTTP endpoint.
    The cache node must have CHAOS_ENABLED=true AND the ChaosController deployed.
    DEV/DEMO ONLY.

    Args:
        node_id: Target node
        target_mb: Amount of memory to allocate in megabytes (careful!)
    """
    validate_chaos_enabled()
    validate_node(node_id)

    import httpx
    from config import settings

    url = settings.node_urls.get(node_id)
    target_mb = max(64, min(target_mb, 1024))  # safety cap

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{url}/api/v1/chaos/memory-pressure",
                params={"targetMb": target_mb},
            )
            result = {
                "node_id": node_id,
                "success": resp.status_code < 300,
                "target_mb": target_mb,
                "response": resp.text,
            }
    except Exception as e:
        result = {"node_id": node_id, "success": False, "error": str(e)}

    record_tool_call(
        tool_name="simulate_high_memory",
        args={"node_id": node_id, "target_mb": target_mb},
        result_summary=f"Memory pressure {'triggered' if result.get('success') else 'failed'} on {node_id}",
        is_mutating=True,
    )
    return result
