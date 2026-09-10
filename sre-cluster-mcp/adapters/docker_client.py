"""
Docker SDK adapter.
Provides read-only inspection and controlled container lifecycle operations.
SECURITY: Only allowlisted container names can be operated on.
"""
import docker
from typing import Any, Dict
from config import settings
from security.allowlist import validate_container


def _get_client():
    try:
        return docker.from_env()
    except Exception as e:
        return None


def _container_name_for_node(node_id: str) -> str:
    """Map node-1 → cache-node-1 etc."""
    mapping = {
        "node-1": "cache-node-1",
        "node-2": "cache-node-2",
        "node-3": "cache-node-3",
    }
    container = mapping.get(node_id)
    if not container:
        raise ValueError(f"No container mapping for node_id '{node_id}'")
    return container


async def inspect_container(node_id: str) -> Dict[str, Any]:
    """
    Read-only Docker inspect. Returns CPU%, memory usage, restart count, status.
    """
    container_name = _container_name_for_node(node_id)
    validate_container(container_name)

    try:
        client = _get_client()
        if not client:
            return {"node_id": node_id, "error": "Docker daemon is not reachable", "container_name": container_name}
        container = client.containers.get(container_name)

        # Get stats (non-streaming, single snapshot)
        stats = container.stats(stream=False)

        # Calculate CPU %
        cpu_delta = stats["cpu_stats"]["cpu_usage"]["total_usage"] - \
                    stats["precpu_stats"]["cpu_usage"]["total_usage"]
        system_delta = stats["cpu_stats"]["system_cpu_usage"] - \
                       stats["precpu_stats"]["system_cpu_usage"]
        num_cpus = stats["cpu_stats"].get("online_cpus", 1)
        cpu_pct = (cpu_delta / system_delta) * num_cpus * 100.0 if system_delta > 0 else 0.0

        # Memory
        mem_usage = stats["memory_stats"].get("usage", 0)
        mem_limit = stats["memory_stats"].get("limit", 1)
        mem_pct = (mem_usage / mem_limit) * 100.0

        inspect_data = container.attrs
        return {
            "node_id": node_id,
            "container_name": container_name,
            "status": container.status,
            "image": container.image.tags,
            "restart_count": inspect_data.get("RestartCount", 0),
            "started_at": inspect_data.get("State", {}).get("StartedAt"),
            "cpu_pct": round(cpu_pct, 2),
            "memory_usage_mb": round(mem_usage / 1024 / 1024, 2),
            "memory_limit_mb": round(mem_limit / 1024 / 1024, 2),
            "memory_pct": round(mem_pct, 2),
            "exit_code": inspect_data.get("State", {}).get("ExitCode"),
            "oom_killed": inspect_data.get("State", {}).get("OOMKilled", False),
        }
    except docker.errors.NotFound:
        return {"node_id": node_id, "error": f"Container '{container_name}' not found"}
    except Exception as e:
        return {"node_id": node_id, "error": str(e)}


async def get_container_logs(node_id: str, tail_lines: int = 100) -> str:
    """
    Fetch raw container logs (tail only). Log compression handled separately.
    """
    container_name = _container_name_for_node(node_id)
    validate_container(container_name)

    try:
        client = _get_client()
        if not client:
            return f"ERROR: Docker daemon not reachable for container '{container_name}'"
        container = client.containers.get(container_name)
        logs = container.logs(tail=min(tail_lines, 500), timestamps=True)
        return logs.decode("utf-8", errors="replace")
    except docker.errors.NotFound:
        return f"ERROR: Container '{container_name}' not found"
    except Exception as e:
        return f"ERROR: {e}"


async def restart_container(node_id: str, timeout_seconds: int = 10) -> Dict[str, Any]:
    """
    Restart a container. MUTATING — must be called only after approval.
    """
    container_name = _container_name_for_node(node_id)
    validate_container(container_name)

    try:
        client = _get_client()
        if not client:
            return {"node_id": node_id, "success": False, "error": "Docker daemon not reachable"}
        container = client.containers.get(container_name)
        old_status = container.status
        container.restart(timeout=timeout_seconds)
        container.reload()
        return {
            "node_id": node_id,
            "container_name": container_name,
            "success": True,
            "status_before": old_status,
            "status_after": container.status,
        }
    except docker.errors.NotFound:
        return {"node_id": node_id, "success": False, "error": f"Container '{container_name}' not found"}
    except Exception as e:
        return {"node_id": node_id, "success": False, "error": str(e)}


async def pause_container(node_id: str) -> Dict[str, Any]:
    """Pause a container (chaos/dev only)."""
    container_name = _container_name_for_node(node_id)
    validate_container(container_name)
    try:
        client = _get_client()
        if not client:
            return {"node_id": node_id, "success": False, "error": "Docker daemon not reachable"}
        container = client.containers.get(container_name)
        container.pause()
        return {"node_id": node_id, "success": True, "action": "paused"}
    except Exception as e:
        return {"node_id": node_id, "success": False, "error": str(e)}


async def unpause_container(node_id: str) -> Dict[str, Any]:
    """Unpause a container."""
    container_name = _container_name_for_node(node_id)
    validate_container(container_name)
    try:
        client = _get_client()
        if not client:
            return {"node_id": node_id, "success": False, "error": "Docker daemon not reachable"}
        container = client.containers.get(container_name)
        container.unpause()
        return {"node_id": node_id, "success": True, "action": "unpaused"}
    except Exception as e:
        return {"node_id": node_id, "success": False, "error": str(e)}
