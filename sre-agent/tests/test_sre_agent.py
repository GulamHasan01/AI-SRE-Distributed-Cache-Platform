"""
Tests for sre-agent incident model, store, OODA loop, and postmortem generation.
"""
import pytest
import asyncio
import os
import sys
from datetime import datetime, timezone

# Add sre-agent to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from incident.model import IncidentRecord, IncidentStatus, IncidentSeverity
from incident.store import IncidentStore
from postmortem.generator import generate_postmortem


def test_incident_lifecycle_model():
    """Verify IncidentRecord state transitions and evidence storage."""
    inc = IncidentRecord(
        affected_service="node-2",
        severity=IncidentSeverity.CRITICAL,
        alert_name="JvmHeapUsageCritical",
        symptoms=["JVM heap at 95%"],
    )
    assert inc.status == IncidentStatus.DETECTED
    assert inc.affected_service == "node-2"

    # Add evidence
    inc.add_evidence("actuator", "get_jvm_diagnostics", {"heap_usage_pct": 95.4})
    assert len(inc.evidence) == 1
    assert "get_jvm_diagnostics" in inc.tools_called

    # Add OODA step
    inc.add_ooda_step("OBSERVE", "Collected JVM metrics", {"heap": 95.4})
    assert len(inc.ooda_steps) == 1
    assert inc.ooda_steps[0].phase == "OBSERVE"

    # Verify dict conversion
    data = inc.to_dict()
    assert data["incident_id"] == inc.incident_id
    assert data["status"] == "DETECTED"
    assert data["evidence_count"] == 1


def test_incident_store_operations():
    """Verify thread-safe in-memory incident store CRUD."""
    store = IncidentStore()
    inc1 = IncidentRecord(affected_service="node-1", status=IncidentStatus.DETECTED)
    inc2 = IncidentRecord(affected_service="node-2", status=IncidentStatus.RESOLVED)

    store.create(inc1)
    store.create(inc2)

    assert len(store.list_all()) == 2
    assert len(store.list_active()) == 1
    assert store.get(inc1.incident_id).affected_service == "node-1"

    inc1.status = IncidentStatus.RESOLVED
    store.update(inc1)
    assert len(store.list_active()) == 0


@pytest.mark.asyncio
async def test_postmortem_generation(tmp_path):
    """Verify postmortem markdown document generation with all required sections."""
    inc = IncidentRecord(
        affected_service="node-2",
        severity=IncidentSeverity.HIGH,
        status=IncidentStatus.RESOLVED,
        alert_name="JvmHeapUsageCritical",
        alert_description="Heap usage exceeded 90%",
        root_cause="Unbounded cache growth exhausted heap memory.",
        confidence="HIGH",
        planned_actions=["drain_node_traffic", "restart_service_node", "restore_node_traffic"],
        actions_executed=["Drained traffic from Gateway", "Restarted container", "Restored traffic"],
        verification_result="All nodes healthy and responsive to ping",
        resolution_time_seconds=42.5,
    )
    inc.add_evidence("actuator", "get_jvm_diagnostics", {"heap_usage_pct": 98.2})
    inc.add_ooda_step("OBSERVE", "Heap check failed")
    inc.add_ooda_step("DECIDE", "Scheduled node restart")

    # Generate postmortem
    path = await generate_postmortem(inc)
    assert os.path.exists(path)

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Check required sections
    assert "Incident Summary" in content
    assert "Impact" in content
    assert "Detection" in content
    assert "Evidence Collected" in content
    assert "OODA Loop Steps" in content
    assert "Root Cause Analysis" in content
    assert "Remediation Actions" in content
    assert "Verification" in content
    assert "Preventive Recommendations" in content
