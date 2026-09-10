"""
MCP Client — calls sre-cluster-mcp tools over HTTP/SSE.
"""
import httpx
import json
from typing import Any, Dict, Optional
from config import settings


class MCPClient:
    """
    HTTP client that calls the MCP server's SSE endpoint to invoke tools.
    In production, this would use the MCP protocol directly.
    For SSE transport, we use the /call endpoint.
    """

    def __init__(self, base_url: str = None):
        self.base_url = (base_url or settings.mcp_server_url).rstrip("/")

    async def call_tool(self, tool_name: str, args: Dict[str, Any]) -> Dict[str, Any]:
        """
        Call an MCP tool by name with the given arguments.
        Returns the tool's response as a dict.
        """
        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(
                f"{self.base_url}/tools/call",
                json={"name": tool_name, "arguments": args},
            )
            resp.raise_for_status()
            result = resp.json()
            # MCP returns content array
            content = result.get("content", [])
            if content and isinstance(content, list):
                text = content[0].get("text", "{}")
                try:
                    return json.loads(text)
                except json.JSONDecodeError:
                    return {"raw": text}
            return result

    async def list_tools(self) -> list:
        """List all available tools from the MCP server."""
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(f"{self.base_url}/tools/list")
            resp.raise_for_status()
            return resp.json().get("tools", [])

    async def is_healthy(self) -> bool:
        """Check if MCP server is reachable."""
        try:
            async with httpx.AsyncClient(timeout=5) as client:
                resp = await client.get(f"{self.base_url}/health")
                return resp.status_code < 300
        except Exception:
            return False


# Global instance
mcp_client = MCPClient()
