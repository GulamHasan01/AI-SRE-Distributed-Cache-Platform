"""
Thread-safe in-memory incident store.
"""
import threading
from typing import Dict, List, Optional
from incident.model import IncidentRecord, IncidentStatus


class IncidentStore:
    """Thread-safe store for all incidents in this agent session."""

    def __init__(self):
        self._incidents: Dict[str, IncidentRecord] = {}
        self._lock = threading.RLock()

    def create(self, incident: IncidentRecord) -> IncidentRecord:
        with self._lock:
            self._incidents[incident.incident_id] = incident
            return incident

    def get(self, incident_id: str) -> Optional[IncidentRecord]:
        with self._lock:
            return self._incidents.get(incident_id)

    def update(self, incident: IncidentRecord) -> IncidentRecord:
        with self._lock:
            self._incidents[incident.incident_id] = incident
            return incident

    def list_all(self) -> List[IncidentRecord]:
        with self._lock:
            return list(self._incidents.values())

    def list_active(self) -> List[IncidentRecord]:
        terminal = {IncidentStatus.RESOLVED, IncidentStatus.FAILED, IncidentStatus.ESCALATED, IncidentStatus.REJECTED}
        with self._lock:
            return [i for i in self._incidents.values() if i.status not in terminal]

    def list_by_status(self, status: IncidentStatus) -> List[IncidentRecord]:
        with self._lock:
            return [i for i in self._incidents.values() if i.status == status]


# Global singleton
incident_store = IncidentStore()
