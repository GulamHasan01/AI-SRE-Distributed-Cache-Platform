"""
sre-cluster-mcp Configuration
All settings loaded from environment variables with sane defaults.
"""
from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    # ============================================================
    # Cache Cluster Node URLs
    # ============================================================
    cache_node_1_url: str = "http://cache-node-1:8081"
    cache_node_2_url: str = "http://cache-node-2:8082"
    cache_node_3_url: str = "http://cache-node-3:8083"
    gateway_url: str = "http://gateway-service:8080"

    # ============================================================
    # Prometheus
    # ============================================================
    prometheus_url: str = "http://prometheus:9091"

    # ============================================================
    # Docker (for container inspection + restart)
    # ============================================================
    docker_socket: str = "unix:///var/run/docker.sock"

    # ============================================================
    # Security
    # ============================================================
    # Allowlisted node IDs the MCP server is allowed to operate on
    allowed_node_ids: str = "node-1,node-2,node-3"
    # Allowlisted container names
    allowed_container_names: str = "cache-node-1,cache-node-2,cache-node-3"
    # Toggle chaos tools (MUST be false in production)
    chaos_enabled: bool = False

    # ============================================================
    # Audit Log
    # ============================================================
    audit_log_path: str = "/app/audit/audit_log.jsonl"

    # ============================================================
    # MCP Server
    # ============================================================
    mcp_server_name: str = "sre-cluster-mcp"
    mcp_server_version: str = "1.0.0"
    http_timeout_seconds: int = 10

    @property
    def node_urls(self) -> dict:
        return {
            "node-1": self.cache_node_1_url,
            "node-2": self.cache_node_2_url,
            "node-3": self.cache_node_3_url,
        }

    @property
    def allowed_nodes_list(self) -> List[str]:
        return [n.strip() for n in self.allowed_node_ids.split(",")]

    @property
    def allowed_containers_list(self) -> List[str]:
        return [c.strip() for c in self.allowed_container_names.split(",")]

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
