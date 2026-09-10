"""
Incident data model and state machine.
Every incident has a well-defined lifecycle with typed fields.
"""
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from enum import Enum
import uuid


class IncidentStatus(str, Enum):
    DETECTED = "DETECTED"
    INVESTIGATING = "INVESTIGATING"
    DIAGNOSED = "DIAGNOSED"
    AWAITING_APPROVAL = "AWAITING_APPROVAL"
    REMEDIATING = "REMEDIATING"
    VERIFYING = "VERIFYING"
    RESOLVED = "RESOLVED"
    ESCALATED = "ESCALATED"
    FAILED = "FAILED"
    REJECTED = "REJECTED"


class IncidentSeverity(str, Enum):
    CRITICAL = "CRITICAL"
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"
    UNKNOWN = "UNKNOWN"


@dataclass
class Evidence:
    """A single piece of evidence collected during investigation."""
    source: str           # e.g., "prometheus", "actuator", "docker", "logs"
    tool_name: str
    data: Dict[str, Any]
    collected_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


@dataclass
class OODAStep:
    """A single step in the OODA loop."""
    phase: str            # OBSERVE, ORIENT, DECIDE, ACT, VERIFY
    summary: str
    details: Dict[str, Any]
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


@dataclass
class IncidentRecord:
    """Full incident record — everything the agent knows and did."""
    incident_id: str = field(default_factory=lambda: f"INC-{datetime.now().strftime('%Y')}-{str(uuid.uuid4())[:6].upper()}")
    affected_service: str = ""
    severity: IncidentSeverity = IncidentSeverity.UNKNOWN
    status: IncidentStatus = IncidentStatus.DETECTED
    detected_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    resolved_at: Optional[str] = None
    resolution_time_seconds: Optional[float] = None

    # Alert context
    alert_name: Optional[str] = None
    alert_description: Optional[str] = None

    # OODA phases
    symptoms: List[str] = field(default_factory=list)
    evidence: List[Evidence] = field(default_factory=list)
    ooda_steps: List[OODAStep] = field(default_factory=list)

    # Diagnosis
    root_cause: Optional[str] = None
    confidence: Optional[str] = None   # "HIGH", "MEDIUM", "LOW"
    diagnosis_summary: Optional[str] = None

    # Remediation
    planned_actions: List[str] = field(default_factory=list)
    approval_id: Optional[str] = None
    approval_status: Optional[str] = None

    # Execution
    actions_executed: List[str] = field(default_factory=list)
    tools_called: List[str] = field(default_factory=list)

    # Verification
    verification_result: Optional[str] = None
    verification_details: Optional[Dict[str, Any]] = None

    # Postmortem
    postmortem_path: Optional[str] = None

    def add_evidence(self, source: str, tool_name: str, data: Dict[str, Any]):
        self.evidence.append(Evidence(source=source, tool_name=tool_name, data=data))
        self.tools_called.append(tool_name)

    def add_ooda_step(self, phase: str, summary: str, details: Dict[str, Any] = None):
        self.ooda_steps.append(OODAStep(phase=phase, summary=summary, details=details or {}))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "incident_id": self.incident_id,
            "affected_service": self.affected_service,
            "severity": self.severity.value,
            "status": self.status.value,
            "detected_at": self.detected_at,
            "resolved_at": self.resolved_at,
            "resolution_time_seconds": self.resolution_time_seconds,
            "alert_name": self.alert_name,
            "alert_description": self.alert_description,
            "symptoms": self.symptoms,
            "evidence_count": len(self.evidence),
            "evidence": [
                {"source": e.source, "tool": e.tool_name, "collected_at": e.collected_at, "summary": str(e.data)[:300]}
                for e in self.evidence
            ],
            "ooda_steps": [
                {"phase": s.phase, "summary": s.summary, "timestamp": s.timestamp}
                for s in self.ooda_steps
            ],
            "root_cause": self.root_cause,
            "confidence": self.confidence,
            "diagnosis_summary": self.diagnosis_summary,
            "planned_actions": self.planned_actions,
            "approval_id": self.approval_id,
            "approval_status": self.approval_status,
            "actions_executed": self.actions_executed,
            "tools_called": self.tools_called,
            "verification_result": self.verification_result,
            "verification_details": self.verification_details,
            "postmortem_path": self.postmortem_path,
        }
