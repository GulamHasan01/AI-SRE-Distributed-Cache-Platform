"""
Autonomous SRE Runbook Engine.
Registry mapping alerts to executable mitigation plans.
"""
from typing import Dict, Any, Optional
from .heap_exhaustion import HeapExhaustionRunbook
from .split_brain import SplitBrainRunbook
from .cache_stampede import CacheStampedeRunbook


class RunbookRegistry:
    _RUNBOOKS = {
        HeapExhaustionRunbook.target_alert: HeapExhaustionRunbook,
        SplitBrainRunbook.target_alert: SplitBrainRunbook,
        CacheStampedeRunbook.target_alert: CacheStampedeRunbook,
    }

    @classmethod
    def get_runbook_for_alert(cls, alert_name: str) -> Optional[Any]:
        return cls._RUNBOOKS.get(alert_name)

    @classmethod
    def list_supported_alerts(cls) -> list:
        return list(cls._RUNBOOKS.keys())


__all__ = [
    "RunbookRegistry",
    "HeapExhaustionRunbook",
    "SplitBrainRunbook",
    "CacheStampedeRunbook",
]
