"""
Human-in-the-Loop approval gate.
Stores pending approvals and lets the agent wait for a human decision.
"""
import asyncio
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, Optional, Literal


ApprovalStatus = Literal["PENDING", "APPROVED", "REJECTED", "TIMEOUT"]


@dataclass
class ApprovalRequest:
    approval_id: str
    incident_id: str
    action: str
    node_id: str
    risk_level: str
    description: str
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    status: ApprovalStatus = "PENDING"
    decided_at: Optional[str] = None
    decided_by: Optional[str] = None
    event: asyncio.Event = field(default_factory=asyncio.Event)

    class Config:
        arbitrary_types_allowed = True


class ApprovalGate:
    """Thread-safe in-memory store for pending approvals."""

    def __init__(self):
        self._pending: Dict[str, ApprovalRequest] = {}

    def create_request(
        self,
        approval_id: str,
        incident_id: str,
        action: str,
        node_id: str,
        risk_level: str,
        description: str,
    ) -> ApprovalRequest:
        req = ApprovalRequest(
            approval_id=approval_id,
            incident_id=incident_id,
            action=action,
            node_id=node_id,
            risk_level=risk_level,
            description=description,
        )
        self._pending[approval_id] = req
        return req

    def get_request(self, approval_id: str) -> Optional[ApprovalRequest]:
        return self._pending.get(approval_id)

    def decide(self, approval_id: str, decision: ApprovalStatus, decided_by: str = "human") -> bool:
        req = self._pending.get(approval_id)
        if not req:
            return False
        req.status = decision
        req.decided_at = datetime.now(timezone.utc).isoformat()
        req.decided_by = decided_by
        req.event.set()  # unblock the waiting agent
        return True

    async def wait_for_decision(
        self, approval_id: str, timeout_seconds: int = 120
    ) -> ApprovalStatus:
        req = self._pending.get(approval_id)
        if not req:
            return "TIMEOUT"
        try:
            await asyncio.wait_for(req.event.wait(), timeout=timeout_seconds)
            return req.status
        except asyncio.TimeoutError:
            req.status = "TIMEOUT"
            return "TIMEOUT"

    def list_pending(self) -> list:
        return [
            {
                "approval_id": r.approval_id,
                "incident_id": r.incident_id,
                "action": r.action,
                "node_id": r.node_id,
                "risk_level": r.risk_level,
                "description": r.description,
                "created_at": r.created_at,
                "status": r.status,
            }
            for r in self._pending.values()
            if r.status == "PENDING"
        ]


# Global singleton
approval_gate = ApprovalGate()
