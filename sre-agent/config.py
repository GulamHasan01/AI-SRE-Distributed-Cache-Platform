"""SRE Agent configuration."""
from pydantic_settings import BaseSettings
from typing import Optional


class AgentSettings(BaseSettings):
    # LLM
    anthropic_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    llm_model: str = "claude-3-5-sonnet-20241022"

    # MCP Server URL (SSE transport)
    mcp_server_url: str = "http://sre-mcp:8000"

    # Agent API port
    agent_api_port: int = 9090

    # Approval timeout
    approval_timeout_seconds: int = 120

    # Post-remediation verification wait
    verification_wait_seconds: int = 30

    # Incident store
    incidents_dir: str = "/app/incidents"

    # Audit
    audit_log_path: str = "/app/audit/agent_audit.jsonl"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = AgentSettings()
