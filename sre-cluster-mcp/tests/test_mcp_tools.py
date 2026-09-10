"""
Tests for sre-cluster-mcp tools, security allowlists, and log compressor.
"""
import pytest
import sys
import os

# Add sre-cluster-mcp to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from security.allowlist import validate_node, validate_container, DisallowedOperationError
from security.approval_gate import approval_gate
from log_processor.compressor import compress_logs, _extract_fingerprint


def test_allowlist_valid_nodes():
    """Verify allowlisted nodes pass validation without error."""
    validate_node("node-1")
    validate_node("node-2")
    validate_node("node-3")


def test_allowlist_disallowed_node():
    """Verify unauthorized node IDs raise DisallowedOperationError."""
    with pytest.raises(DisallowedOperationError):
        validate_node("node-99")
    with pytest.raises(DisallowedOperationError):
        validate_node("../../etc/passwd")


def test_allowlist_containers():
    """Verify allowlisted containers pass and disallowed ones fail."""
    validate_container("cache-node-1")
    validate_container("cache-node-2")
    validate_container("cache-node-3")

    with pytest.raises(DisallowedOperationError):
        validate_container("malicious-container")


def test_approval_gate_lifecycle():
    """Verify approval request creation, pending state, and approval decision."""
    req = approval_gate.create_request(
        approval_id="test-appr-01",
        incident_id="INC-2026-TEST",
        action="restart_service_node",
        node_id="node-2",
        risk_level="HIGH",
        description="Restart node-2 after memory pressure",
    )
    assert req.approval_id == "test-appr-01"
    assert req.status == "PENDING"

    pending = approval_gate.list_pending()
    assert any(p["approval_id"] == "test-appr-01" for p in pending)

    # Approve
    decided = approval_gate.decide("test-appr-01", "APPROVED", decided_by="sre_test")
    assert decided is True

    updated_req = approval_gate.get_request("test-appr-01")
    assert updated_req.status == "APPROVED"
    assert updated_req.decided_by == "sre_test"


def test_log_compressor_deduplication():
    """Verify repetitive log lines are grouped into a single pattern with accurate count."""
    repetitive_logs = "\n".join([
        "2026-09-08 10:00:00 [http-nio-8082-exec-1] ERROR com.cache.store.InMemoryCacheStore - java.lang.OutOfMemoryError: Java heap space"
        for _ in range(50)
    ])

    result = compress_logs(repetitive_logs, severity_filter="ERROR")
    assert result["total_raw_lines"] == 50
    assert result["pattern_count"] == 1
    assert result["patterns"][0]["occurrences"] == 50
    assert any("OutOfMemoryError" in exc for exc in result["patterns"][0]["exceptions"])


def test_log_compressor_extracts_stack_traces():
    """Verify stack trace lines following an exception are captured."""
    logs_with_stack = """
2026-09-08 10:00:00 [main] ERROR com.cache.controller.CacheController - Unexpected failure
    at com.cache.service.impl.CacheServiceImpl.put(CacheServiceImpl.java:45)
    at com.cache.controller.CacheController.put(CacheController.java:85)
"""
    result = compress_logs(logs_with_stack)
    assert result["pattern_count"] >= 1
    pattern = result["patterns"][0]
    assert len(pattern["stack_trace_sample"]) >= 2
