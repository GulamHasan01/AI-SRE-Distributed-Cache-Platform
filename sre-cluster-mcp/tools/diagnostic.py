"""
MCP Diagnostic Tools (Read-Only).
These tools allow the AI agent to safely observe cluster state without modifying anything.
"""
from typing import Optional
from adapters import prometheus_client, actuator_client, docker_client, cache_api_client
from log_processor.compressor import compress_logs
from security.allowlist import validate_node
from security.audit_log import record_tool_call


async def get_recent_logs(
    service_name: str,
    tail_lines: int = 100,
    severity: Optional[str] = "ERROR",
) -> dict:
    """
    Fetch and compress recent logs for a cache node.

    Args:
        service_name: Node ID (e.g., "node-1", "node-2", "node-3") or "gateway"
        tail_lines: Number of raw log lines to fetch (max 500)
        severity: Minimum severity to include (ERROR, WARN, INFO, etc.)

    Returns:
        Compressed log evidence with patterns, frequencies, and stack traces.
    """
    validate_node(service_name) if service_name != "gateway" else None
    tail_lines = max(10, min(tail_lines, 500))

    raw_logs = await docker_client.get_container_logs(service_name, tail_lines)
    compressed = compress_logs(raw_logs, severity_filter=severity)

    record_tool_call(
        tool_name="get_recent_logs",
        args={"service_name": service_name, "tail_lines": tail_lines, "severity": severity},
        result_summary=f"Fetched {compressed['total_raw_lines']} lines, found {compressed['pattern_count']} patterns",
    )
    return compressed


async def query_prometheus_metric(
    query: str,
    duration: str = "5m",
) -> dict:
    """
    Execute a PromQL query against Prometheus.

    Args:
        query: Valid PromQL expression (e.g., 'jvm_memory_used_bytes{job="cache-node-2"}')
        duration: Time range for range queries (e.g., '5m', '1h', '30s')

    Returns:
        Structured query result with labels and values.
    """
    result = await prometheus_client.query_range(query, duration=duration)
    record_tool_call(
        tool_name="query_prometheus_metric",
        args={"query": query, "duration": duration},
        result_summary=f"Returned {result.get('series_count', 0)} series",
    )
    return result


async def get_jvm_diagnostics(node_id: str) -> dict:
    """
    Retrieve JVM metrics from a specific cache node via Spring Boot Actuator.

    Args:
        node_id: Cache node ID ("node-1", "node-2", "node-3")

    Returns:
        JVM heap usage, thread counts, GC pauses, CPU usage, and cache stats.
    """
    validate_node(node_id)
    result = await actuator_client.get_metrics(node_id)
    record_tool_call(
        tool_name="get_jvm_diagnostics",
        args={"node_id": node_id},
        result_summary=f"Heap usage: {result.get('heap_usage_pct')}%",
    )
    return result


async def get_thread_dump(node_id: str) -> dict:
    """
    Retrieve a thread dump from a specific cache node via Spring Boot Actuator.
    Useful for diagnosing deadlocks or thread pool starvation.

    Args:
        node_id: Cache node ID ("node-1", "node-2", "node-3")

    Returns:
        Thread count by state, list of blocked/waiting threads with stack tops.
    """
    validate_node(node_id)
    result = await actuator_client.get_thread_dump(node_id)
    record_tool_call(
        tool_name="get_thread_dump",
        args={"node_id": node_id},
        result_summary=f"Total threads: {result.get('total_threads')}, state summary: {result.get('state_summary')}",
    )
    return result


async def get_cluster_topology() -> dict:
    """
    Retrieve the current cluster topology including node statuses and consistent hash ring.

    Returns:
        All registered nodes with status (UP/SUSPECT/DOWN), heartbeat age, and ring metadata.
    """
    result = await cache_api_client.get_cluster_status()
    record_tool_call(
        tool_name="get_cluster_topology",
        args={},
        result_summary=f"Source: {result.get('source_node', 'unknown')}",
    )
    return result


async def inspect_container(node_id: str) -> dict:
    """
    Inspect a cache node's Docker container for resource usage and health.

    Args:
        node_id: Cache node ID ("node-1", "node-2", "node-3")

    Returns:
        CPU%, memory usage, restart count, OOMKilled flag, and container status.
    """
    validate_node(node_id)
    result = await docker_client.inspect_container(node_id)
    record_tool_call(
        tool_name="inspect_container",
        args={"node_id": node_id},
        result_summary=f"CPU: {result.get('cpu_pct')}%, Mem: {result.get('memory_pct')}%, OOM: {result.get('oom_killed')}",
    )
    return result


async def get_node_health(node_id: str) -> dict:
    """
    Get the health status of a specific node including heartbeat age and cluster view.

    Args:
        node_id: Cache node ID ("node-1", "node-2", "node-3")

    Returns:
        Actuator health + cluster registry heartbeat status for the node.
    """
    validate_node(node_id)
    actuator_health = await actuator_client.get_health(node_id)
    cluster_health = await cache_api_client.get_node_health_status(node_id)
    ping_result = await cache_api_client.ping_node(node_id)

    result = {
        "node_id": node_id,
        "ping_alive": ping_result.get("alive"),
        "actuator_health": actuator_health,
        "cluster_registry_view": cluster_health,
    }
    record_tool_call(
        tool_name="get_node_health",
        args={"node_id": node_id},
        result_summary=f"Ping: {ping_result.get('alive')}, Actuator: {actuator_health.get('health', {}).get('status')}",
    )
    return result


async def get_active_prometheus_alerts() -> dict:
    """
    Fetch all currently firing Prometheus alerts.

    Returns:
        List of active alerts with severity, node, and description.
    """
    alerts = await prometheus_client.get_active_alerts()
    record_tool_call(
        tool_name="get_active_prometheus_alerts",
        args={},
        result_summary=f"Found {len(alerts)} firing alerts",
    )
    return {"firing_alerts": alerts, "count": len(alerts)}
