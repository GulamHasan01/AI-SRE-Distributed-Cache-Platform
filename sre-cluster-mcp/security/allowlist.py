"""
Security: Allowlist validation.
The MCP server may ONLY operate on explicitly allowed node IDs and container names.
Any attempt to operate outside the allowlist raises ValueError — the LLM never gets raw access.
"""
from config import settings


class DisallowedOperationError(ValueError):
    """Raised when an operation targets an unlisted node or container."""
    pass


def validate_node(node_id: str) -> str:
    """
    Validate that the given node_id is in the allowlist.
    Returns node_id on success, raises DisallowedOperationError on failure.
    """
    allowed = settings.allowed_nodes_list
    if node_id not in allowed:
        raise DisallowedOperationError(
            f"Node '{node_id}' is not in the allowlist. "
            f"Allowed nodes: {allowed}. "
            f"The MCP server cannot operate on unlisted nodes."
        )
    return node_id


def validate_container(container_name: str) -> str:
    """
    Validate that the container name is in the allowlist.
    """
    allowed = settings.allowed_containers_list
    if container_name not in allowed:
        raise DisallowedOperationError(
            f"Container '{container_name}' is not in the allowlist. "
            f"Allowed containers: {allowed}."
        )
    return container_name


def validate_chaos_enabled() -> None:
    """Raise if chaos tools are called in non-chaos mode."""
    if not settings.chaos_enabled:
        raise ValueError(
            "Chaos tools are disabled. Set CHAOS_ENABLED=true in environment "
            "to enable chaos injection (development/demo only)."
        )
