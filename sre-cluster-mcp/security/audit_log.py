"""
Append-only audit log for all MCP tool calls.
Every tool invocation (read or mutating) is recorded with timestamp, tool name, args, result summary.
"""
import json
import os
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from config import settings


def _ensure_audit_dir():
    os.makedirs(os.path.dirname(settings.audit_log_path), exist_ok=True)


def record_tool_call(
    tool_name: str,
    args: Dict[str, Any],
    result_summary: str,
    incident_id: Optional[str] = None,
    approved_by: Optional[str] = None,
    is_mutating: bool = False,
    success: bool = True,
    error: Optional[str] = None,
) -> None:
    """
    Append a structured audit event to the audit log file.
    """
    _ensure_audit_dir()
    event = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "tool": tool_name,
        "args": args,
        "incident_id": incident_id,
        "is_mutating": is_mutating,
        "approved_by": approved_by,
        "success": success,
        "result_summary": result_summary,
        "error": error,
    }
    with open(settings.audit_log_path, "a", encoding="utf-8") as f:
        f.write(json.dumps(event) + "\n")


def get_recent_audit_events(limit: int = 50) -> list:
    """Read the last N audit events."""
    try:
        with open(settings.audit_log_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        events = [json.loads(line) for line in lines if line.strip()]
        return events[-limit:]
    except FileNotFoundError:
        return []
