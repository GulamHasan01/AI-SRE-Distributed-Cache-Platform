"""
Unit tests for SRE automated runbooks and execution plans.
"""
import pytest
import os
import sys

# Add sre-agent to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from runbooks import (
    RunbookRegistry,
    HeapExhaustionRunbook,
    SplitBrainRunbook,
    CacheStampedeRunbook,
)


def test_runbook_registry_lookup():
    """Verify registry resolves runbooks correctly for alert types."""
    heap_rb = RunbookRegistry.get_runbook_for_alert("JvmHeapUsageCritical")
    assert heap_rb is HeapExhaustionRunbook

    split_rb = RunbookRegistry.get_runbook_for_alert("NodeHeartbeatMissing")
    assert split_rb is SplitBrainRunbook

    stampede_rb = RunbookRegistry.get_runbook_for_alert("P99LatencySpike")
    assert stampede_rb is CacheStampedeRunbook

    assert RunbookRegistry.get_runbook_for_alert("NonExistentAlert") is None


def test_heap_exhaustion_runbook_steps():
    """Verify heap exhaustion execution plan generation."""
    plan = HeapExhaustionRunbook.execute_plan(node_id="node-1", current_heap_pct=94.5)
    assert len(plan) == 4
    assert plan[0]["action"] == "diagnose_heap"
    assert plan[1]["action"] == "drain_traffic"
    assert plan[2]["requires_approval"] is True
    assert plan[3]["action"] == "verify_restoration"


def test_split_brain_runbook_steps():
    """Verify split-brain partition remediation plan."""
    plan = SplitBrainRunbook.execute_plan(dropout_node_id="node-3", active_nodes=["node-1", "node-2"])
    assert len(plan) == 4
    assert plan[0]["action"] == "check_cluster_quorum"
    assert plan[1]["params"]["node_id"] == "node-3"
    assert plan[2]["action"] == "rebalance_ring"


def test_cache_stampede_runbook_steps():
    """Verify cache stampede mitigation plan."""
    plan = CacheStampedeRunbook.execute_plan(target_service="gateway", p99_latency_ms=120.0)
    assert len(plan) == 3
    assert plan[1]["action"] == "enable_stampede_protection"
