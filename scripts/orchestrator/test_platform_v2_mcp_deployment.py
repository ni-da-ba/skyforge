from __future__ import annotations

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class McpDeploymentContractTest(unittest.TestCase):
    def test_official_mcp_sdk_is_pinned(self):
        requirements = (ROOT / "scripts/orchestrator/requirements.txt").read_text()
        self.assertIn("mcp==2.2.0", requirements.splitlines())

    def test_sidecar_is_localhost_only_and_reuses_protected_environment(self):
        service = (
            ROOT / "deploy/orchestrator/skyforge-mcp.service.in"
        ).read_text(encoding="utf-8")
        self.assertIn("EnvironmentFile=/etc/skyforge-orchestrator/env", service)
        self.assertIn("SKYFORGE_MCP_BACKEND_URL=http://127.0.0.1:3000", service)
        self.assertIn("--bind 127.0.0.1 --port 3001", service)
        self.assertIn("ProtectSystem=strict", service)
        self.assertIn("ProtectHome=read-only", service)
        self.assertIn("CapabilityBoundingSet=", service)
        self.assertNotIn("0.0.0.0", service)

    def test_public_caddy_does_not_route_mcp_sidecar(self):
        caddy = (ROOT / "deploy/orchestrator/Caddyfile.in").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("3001", caddy)
        self.assertNotIn("/mcp", caddy)

    def test_installer_syncs_dependencies_and_protocol_probes_without_printing_tokens(self):
        installer = (
            ROOT / "scripts/orchestrator/install_mcp_adapter.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("sync_runtime_dependencies.py", installer)
        self.assertIn('sudo test -r "$ENV_FILE"', installer)
        self.assertIn("platform_v2_mcp_probe.py", installer)
        self.assertIn("systemctl enable --now skyforge-mcp.service", installer)
        self.assertIn("127.0.0.1:3001/mcp", installer)
        self.assertNotIn("echo \"$read_token\"", installer)
        self.assertNotIn("echo \"$write_token\"", installer)

    def test_adapter_has_no_direct_project_state_or_command_execution_capability(self):
        source = (
            ROOT / "scripts/orchestrator/platform_v2_mcp_server.py"
        ).read_text(encoding="utf-8")
        for forbidden in (
            "v2.state_store",
            "JsonStateStore",
            ".skyforge-platform-v2",
            "subprocess",
            "os.system",
            "shell=True",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn("/api/v1/development-state", source)
        self.assertIn("/api/v1/artifacts", source)
        self.assertIn("/api/v1/objectives", source)
        self.assertIn("/api/v1/human-reviews", source)

    def test_probe_requires_read_tools_and_conditionally_write_tools(self):
        source = (
            ROOT / "scripts/orchestrator/platform_v2_mcp_probe.py"
        ).read_text(encoding="utf-8")
        for name in (
            "get_development_state",
            "list_review_artifacts",
            "get_review_artifact",
            "read_review_artifact_content",
            "submit_objective",
            "submit_human_review",
        ):
            self.assertIn(name, source)
        self.assertIn("--expect-write", source)


if __name__ == "__main__":
    unittest.main()
