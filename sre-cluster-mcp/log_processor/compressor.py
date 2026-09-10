"""
Log Compression Engine.
Compresses raw Docker/Spring Boot logs before sending to LLM.
Reduces 5000 identical lines → compact pattern summary with frequency + stack trace.
"""
import re
from collections import defaultdict
from dataclasses import dataclass, field
from typing import List, Optional, Dict


# Known severity keywords
SEVERITY_PATTERN = re.compile(
    r"\b(ERROR|FATAL|WARN|WARNING|DEBUG|INFO|TRACE)\b", re.IGNORECASE
)

# Match Java stack trace lines (after line.strip())
STACK_TRACE_LINE = re.compile(r"^at\s+[\w\.$<>]+\([\w.:\d]+\)")

# Java exception name pattern
EXCEPTION_PATTERN = re.compile(
    r"([\w.]+Exception|[\w.]+Error|[\w.]+Timeout|java\.lang\.[\w]+)"
)

# Timestamp pattern (ISO or bracket-style)
TIMESTAMP_PATTERN = re.compile(
    r"(\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2})"
)


@dataclass
class LogPattern:
    """Represents a group of repeated log lines."""
    fingerprint: str
    occurrences: int = 0
    first_seen: Optional[str] = None
    last_seen: Optional[str] = None
    representative_line: str = ""
    stack_trace: List[str] = field(default_factory=list)
    severity: str = "UNKNOWN"
    exceptions_found: List[str] = field(default_factory=list)


def _extract_fingerprint(line: str) -> str:
    """
    Create a canonical fingerprint for a log line by removing variable parts:
    timestamps, hex addresses, UUIDs, numbers.
    """
    fp = TIMESTAMP_PATTERN.sub("<TS>", line)
    fp = re.sub(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", "<UUID>", fp)
    fp = re.sub(r"0x[0-9a-fA-F]+", "<HEX>", fp)
    fp = re.sub(r"\b\d{5,}\b", "<NUM>", fp)
    fp = re.sub(r"\[traceId=[^\]]*\]", "[traceId=<X>]", fp)
    return fp.strip()[:200]  # cap length


def _extract_timestamp(line: str) -> Optional[str]:
    m = TIMESTAMP_PATTERN.search(line)
    return m.group(1) if m else None


def _extract_severity(line: str) -> str:
    m = SEVERITY_PATTERN.search(line)
    return m.group(1).upper() if m else "UNKNOWN"


def compress_logs(
    raw_logs: str,
    severity_filter: Optional[str] = None,
    max_patterns: int = 20,
    max_stack_lines: int = 8,
) -> Dict:
    """
    Main compression entry point.

    Args:
        raw_logs: Raw multi-line log text
        severity_filter: If set (e.g. "ERROR"), only include lines at that level or above
        max_patterns: Maximum distinct patterns to return
        max_stack_lines: Maximum stack trace lines to include per pattern

    Returns:
        Structured dict with compressed evidence for LLM
    """
    SEVERITY_RANK = {"FATAL": 0, "ERROR": 1, "WARN": 2, "WARNING": 2, "INFO": 3, "DEBUG": 4, "TRACE": 5}
    filter_rank = SEVERITY_RANK.get((severity_filter or "").upper(), 99)

    lines = raw_logs.splitlines()
    patterns: Dict[str, LogPattern] = {}
    current_pattern_key: Optional[str] = None
    total_lines = len(lines)

    for line in lines:
        line = line.strip()
        if not line:
            continue

        # Detect stack trace continuation
        if STACK_TRACE_LINE.match(line):
            if current_pattern_key and current_pattern_key in patterns:
                p = patterns[current_pattern_key]
                if len(p.stack_trace) < max_stack_lines:
                    p.stack_trace.append(line.strip())
            continue

        severity = _extract_severity(line)
        sev_rank = SEVERITY_RANK.get(severity, 99)

        # Apply severity filter
        if severity_filter and sev_rank > filter_rank:
            continue

        timestamp = _extract_timestamp(line)
        fingerprint = _extract_fingerprint(line)
        exceptions = EXCEPTION_PATTERN.findall(line)

        if fingerprint not in patterns:
            if len(patterns) >= max_patterns:
                continue  # don't exceed max patterns
            patterns[fingerprint] = LogPattern(
                fingerprint=fingerprint,
                representative_line=line[:300],
                severity=severity,
                first_seen=timestamp,
                last_seen=timestamp,
                exceptions_found=list(set(exceptions)),
            )

        p = patterns[fingerprint]
        p.occurrences += 1
        if timestamp:
            if p.first_seen is None:
                p.first_seen = timestamp
            p.last_seen = timestamp
        for exc in exceptions:
            if exc not in p.exceptions_found:
                p.exceptions_found.append(exc)

        current_pattern_key = fingerprint

    # Sort patterns: errors first, then by frequency
    sorted_patterns = sorted(
        patterns.values(),
        key=lambda p: (SEVERITY_RANK.get(p.severity, 99), -p.occurrences),
    )

    return {
        "total_raw_lines": total_lines,
        "pattern_count": len(sorted_patterns),
        "severity_filter": severity_filter,
        "patterns": [
            {
                "severity": p.severity,
                "occurrences": p.occurrences,
                "first_seen": p.first_seen,
                "last_seen": p.last_seen,
                "representative_line": p.representative_line,
                "exceptions": p.exceptions_found,
                "stack_trace_sample": p.stack_trace,
            }
            for p in sorted_patterns
        ],
    }
