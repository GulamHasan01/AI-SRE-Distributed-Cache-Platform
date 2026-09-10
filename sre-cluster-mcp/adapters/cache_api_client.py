"""
Cache Platform REST API adapter.
Calls existing endpoints on the gateway and cache nodes.
"""
import httpx
from typing import Any, Dict, List
from config import settings


async def get_cluster_status() -> Dict[str, Any]:
    """GET /api/v1/cluster/status from any reachable node."""
    for node_id, url in settings.node_urls.items():
        try:
            async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
                resp = await client.get(f"{url}/api/v1/cluster/status")
                if resp.status_code == 200:
                    data = resp.json()
                    return {
                        "source_node": node_id,
                        "cluster": data.get("data", {}),
                    }
        except Exception:
            continue
    return {"error": "No cache nodes reachable"}


async def get_node_health_status(node_id: str) -> Dict[str, Any]:
    """
    GET /api/v1/cluster/health/status from a specific node.
    Returns heartbeat age, SUSPECT/DOWN thresholds for all peers.
    """
    url = settings.node_urls.get(node_id)
    if not url:
        return {"error": f"Unknown node_id: {node_id}"}
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.get(f"{url}/api/v1/cluster/health/status")
            resp.raise_for_status()
            return {"node_id": node_id, "reachable": True, "health_status": resp.json().get("data", {})}
    except httpx.HTTPError as e:
        return {"node_id": node_id, "reachable": False, "error": str(e)}


async def get_cache_stats_all() -> List[Dict[str, Any]]:
    """GET /api/v1/cache/stats from all nodes."""
    results = []
    for node_id, url in settings.node_urls.items():
        try:
            async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
                resp = await client.get(f"{url}/api/v1/cache/stats")
                if resp.status_code == 200:
                    results.append({
                        "node_id": node_id,
                        "reachable": True,
                        "stats": resp.json().get("data", {}),
                    })
                else:
                    results.append({"node_id": node_id, "reachable": False})
        except Exception as e:
            results.append({"node_id": node_id, "reachable": False, "error": str(e)})
    return results


async def drain_node_traffic(node_id: str) -> Dict[str, Any]:
    """
    PUT /api/v1/gateway/cluster/nodes/{nodeId}/status?status=DOWN
    Removes node from consistent hash ring — traffic is rerouted.
    """
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.put(
                f"{settings.gateway_url}/api/v1/gateway/cluster/nodes/{node_id}/status",
                params={"status": "DOWN"},
            )
            resp.raise_for_status()
            return {"node_id": node_id, "action": "drain", "success": True, "response": resp.json()}
    except httpx.HTTPError as e:
        return {"node_id": node_id, "action": "drain", "success": False, "error": str(e)}


async def restore_node_traffic(node_id: str) -> Dict[str, Any]:
    """
    PUT /api/v1/gateway/cluster/nodes/{nodeId}/status?status=UP
    Re-adds node to consistent hash ring.
    """
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.put(
                f"{settings.gateway_url}/api/v1/gateway/cluster/nodes/{node_id}/status",
                params={"status": "UP"},
            )
            resp.raise_for_status()
            return {"node_id": node_id, "action": "restore", "success": True, "response": resp.json()}
    except httpx.HTTPError as e:
        return {"node_id": node_id, "action": "restore", "success": False, "error": str(e)}


async def evict_cache_node(node_id: str) -> Dict[str, Any]:
    """DELETE /api/v1/cache — clear local store on a specific node."""
    url = settings.node_urls.get(node_id)
    if not url:
        return {"error": f"Unknown node_id: {node_id}"}
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.delete(f"{url}/api/v1/cache")
            resp.raise_for_status()
            return {"node_id": node_id, "action": "evict_all", "success": True, "response": resp.json()}
    except httpx.HTTPError as e:
        return {"node_id": node_id, "action": "evict_all", "success": False, "error": str(e)}


async def ping_node(node_id: str) -> Dict[str, Any]:
    """GET /api/v1/cluster/health/ping — liveness check."""
    url = settings.node_urls.get(node_id)
    if not url:
        return {"node_id": node_id, "alive": False, "error": "Unknown node"}
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{url}/api/v1/cluster/health/ping")
            return {"node_id": node_id, "alive": resp.status_code == 200}
    except Exception:
        return {"node_id": node_id, "alive": False}


async def get_cluster_metrics() -> Dict[str, Any]:
    """GET /api/v1/gateway/cluster/metrics — aggregated stats from gateway."""
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.get(f"{settings.gateway_url}/api/v1/gateway/cluster/metrics")
            resp.raise_for_status()
            return {"success": True, "metrics": resp.json().get("data", {})}
    except Exception as e:
        return {"success": False, "error": str(e)}
