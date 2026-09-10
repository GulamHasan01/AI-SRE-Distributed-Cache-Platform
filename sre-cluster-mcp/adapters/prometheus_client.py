"""
Prometheus HTTP API adapter.
Executes PromQL queries and returns structured results.
"""
import httpx
from typing import Any, Dict, List
from config import settings


async def query_instant(promql: str) -> Dict[str, Any]:
    """
    Execute an instant PromQL query (current value).
    Returns parsed result dict.
    """
    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        resp = await client.get(
            f"{settings.prometheus_url}/api/v1/query",
            params={"query": promql},
        )
        resp.raise_for_status()
        data = resp.json()

    results = data.get("data", {}).get("result", [])
    parsed = []
    for r in results:
        parsed.append({
            "labels": r.get("metric", {}),
            "value": r.get("value", [None, None])[1],
            "timestamp": r.get("value", [None, None])[0],
        })
    return {
        "query": promql,
        "status": data.get("status"),
        "result_count": len(parsed),
        "results": parsed,
    }


async def query_range(promql: str, duration: str = "5m", step: str = "15s") -> Dict[str, Any]:
    """
    Execute a range PromQL query over the given duration (e.g., '5m', '1h').
    """
    import time
    end = int(time.time())
    # parse duration to seconds
    unit = duration[-1]
    value = int(duration[:-1])
    seconds = {"s": 1, "m": 60, "h": 3600, "d": 86400}.get(unit, 60) * value
    start = end - seconds

    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        resp = await client.get(
            f"{settings.prometheus_url}/api/v1/query_range",
            params={"query": promql, "start": start, "end": end, "step": step},
        )
        resp.raise_for_status()
        data = resp.json()

    results = data.get("data", {}).get("result", [])
    return {
        "query": promql,
        "duration": duration,
        "status": data.get("status"),
        "series_count": len(results),
        "results": [
            {
                "labels": r.get("metric", {}),
                "values": r.get("values", []),
            }
            for r in results
        ],
    }


async def get_active_alerts() -> List[Dict[str, Any]]:
    """Fetch all currently firing alerts from Prometheus."""
    async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
        resp = await client.get(f"{settings.prometheus_url}/api/v1/alerts")
        resp.raise_for_status()
        data = resp.json()

    alerts = data.get("data", {}).get("alerts", [])
    return [
        {
            "name": a.get("labels", {}).get("alertname"),
            "severity": a.get("labels", {}).get("severity"),
            "node_id": a.get("labels", {}).get("node_id"),
            "state": a.get("state"),
            "summary": a.get("annotations", {}).get("summary"),
            "description": a.get("annotations", {}).get("description"),
            "fired_at": a.get("activeAt"),
        }
        for a in alerts
        if a.get("state") == "firing"
    ]
