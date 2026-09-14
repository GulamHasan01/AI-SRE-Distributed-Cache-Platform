"""
Automated Runbook: Split Brain & Partition Isolation.
Triggers when: Heartbeat missing from one or more peer nodes.
Steps:
1. Query Gateway and active nodes for quorum status.
2. If quorum preserved, temporarily isolate dropout node.
3. Re-balance consistent hash ring tokens across healthy quorum.
4. Notify on-call and monitor for automatic peer re-join.
"""
from typing import Dict, Any, List


class SplitBrainRunbook:
    name: str = "runbook_split_brain"
    target_alert: str = "NodeHeartbeatMissing"

    @staticmethod
    def execute_plan(dropout_node_id: str, active_nodes: List[str]) -> List[Dict[str, Any]]:
        steps = [
            {
                "step": 1,
                "action": "check_cluster_quorum",
                "tool": "get_cluster_topology",
                "params": {},
                "critical": True,
            },
            {
                "step": 2,
                "action": "isolate_suspect_node",
                "tool": "drain_node_traffic",
                "params": {"node_id": dropout_node_id},
                "requires_approval": False,
            },
            {
                "step": 3,
                "action": "rebalance_ring",
                "tool": "rebalance_hash_ring",
                "params": {"active_nodes": active_nodes},
                "requires_approval": False,
            },
            {
                "step": 4,
                "action": "verify_quorum_integrity",
                "tool": "get_cluster_topology",
                "params": {"min_healthy": len(active_nodes)},
                "critical": True,
            },
        ]
        return steps
