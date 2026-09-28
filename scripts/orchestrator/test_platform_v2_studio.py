from __future__ import annotations

from http.server import ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import urllib.error
import urllib.request
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_development_read_api import API_TOKEN, make_git_root
from test_platform_v2_hosted_runtime import SECRET, write_legacy


class SkyforgeStudioTest(unittest.TestCase):
    def runtime(self, root: Path) -> hosted.HostedV2Substrate:
        make_git_root(root)
        write_legacy(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            development_api_token=API_TOKEN,
            trusted_actors=("ni-da-ba",),
        )

    def serve(self, runtime: hosted.HostedV2Substrate) -> str:
        hosted.Handler.runtime = runtime
        server = ThreadingHTTPServer(("127.0.0.1", 0), hosted.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.addCleanup(thread.join, 2)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def request(self, url: str):
        return urllib.request.urlopen(url, timeout=3)

    def test_studio_static_routes_use_console_security_boundary(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            for path, expected_type in (
                ("/studio", "text/html"),
                ("/studio/", "text/html"),
                ("/studio/app.js", "text/javascript"),
                ("/studio/scene.js", "text/javascript"),
                ("/studio/styles.css", "text/css"),
            ):
                with self.request(base + path) as response:
                    body = response.read().decode("utf-8")
                    self.assertEqual(response.status, 200)
                    self.assertTrue(
                        response.headers["Content-Type"].startswith(expected_type)
                    )
                    self.assertEqual(response.headers["Cache-Control"], "no-store")
                    self.assertEqual(
                        response.headers["Referrer-Policy"],
                        "no-referrer",
                    )
                    self.assertEqual(
                        response.headers["X-Content-Type-Options"],
                        "nosniff",
                    )
                    csp = response.headers["Content-Security-Policy"]
                    self.assertIn("default-src 'self'", csp)
                    self.assertIn("script-src 'self'", csp)
                    self.assertIn("connect-src 'self'", csp)
                    self.assertIn("object-src 'none'", csp)
                    self.assertIn("frame-ancestors 'none'", csp)
                    self.assertNotIn(API_TOKEN, body)
                    self.assertNotIn(
                        "SKYFORGE_DEVELOPMENT_API_TOKEN",
                        body,
                    )

    def test_unknown_studio_path_is_not_directory_browsing(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = self.runtime(Path(td))
            base = self.serve(runtime)
            with self.assertRaises(urllib.error.HTTPError) as raised:
                self.request(base + "/studio/../../.git/config")
            self.assertEqual(raised.exception.code, 404)

    def test_studio_is_read_only_client_of_existing_development_api(self):
        studio = Path(hosted.__file__).resolve().parent / "studio"
        source = (studio / "app.js").read_text(encoding="utf-8")
        markup = (studio / "index.html").read_text(encoding="utf-8")

        self.assertIn("/api/v1/development-state", source)
        self.assertIn("/api/v1/artifacts", source)
        self.assertIn('/content"', source)
        self.assertIn('headers.set("Authorization"', source)
        self.assertIn("sessionStorage", source)
        self.assertIn("REGISTERED_ARTIFACT", source)
        self.assertIn("UNBOUND_LOCAL", source)
        self.assertIn("reviewAuthority: true", source)
        self.assertIn("reviewAuthority: false", source)
        self.assertIn("not review authority", source)

        self.assertNotIn("/api/v1/objectives", source)
        self.assertNotIn("/api/v1/human-reviews", source)
        self.assertNotIn("/api/v1/objective-controls", source)
        self.assertNotIn("writeToken", source)
        self.assertNotIn("innerHTML", source)
        self.assertNotIn("outerHTML", source)
        self.assertNotIn("insertAdjacentHTML", source)
        self.assertNotIn("document.write", source)
        self.assertNotIn("?token=", source)
        self.assertNotIn("access_token=", source)

        self.assertIn("Backend-neutral semantic inspection", markup)
        self.assertIn("UNBOUND LOCAL DIAGNOSTIC", markup)
        self.assertIn('href="/console"', markup)

    def test_scene_contract_is_authority_neutral_and_supports_exact_semantics(self):
        source = (
            Path(hosted.__file__).resolve().parent
            / "studio"
            / "scene.js"
        ).read_text(encoding="utf-8")
        app = (
            Path(hosted.__file__).resolve().parent
            / "studio"
            / "app.js"
        ).read_text(encoding="utf-8")
        markup = (
            Path(hosted.__file__).resolve().parent
            / "studio"
            / "index.html"
        ).read_text(encoding="utf-8")

        self.assertIn(
            'const ATMOSPHERE_KIND = "SKYFORGE_ATMOSPHERE_PROBE_VOLUME"',
            source,
        )
        self.assertIn(
            'const TERRAIN_KIND = "SKYFORGE_TERRAIN_SEMANTIC_VOLUME"',
            source,
        )
        self.assertIn('primitive: "VectorField"', source)
        self.assertIn('primitive: "PointSet"', source)
        self.assertIn('primitive: "SemanticVolume"', source)
        self.assertIn('sceneKind: "ATMOSPHERE_VECTOR_FIELD"', source)
        self.assertIn('sceneKind: "TERRAIN_SEMANTIC_VOLUME"', source)
        self.assertIn('semanticOwner: "Aerodynamics4MC"', source)
        self.assertIn('semanticOwner: "Skyforge WorldRegionTerrain"', source)
        self.assertIn('artifact.encoding?.kind !== "BASE64_UINT8_ORDINAL"', source)
        self.assertIn(
            '"x + x_samples * (z + z_samples * y)"',
            source,
        )
        self.assertIn("terrain grid sample_count does not match dimensions", source)
        self.assertIn("terrain semantic payload length does not match grid", source)
        self.assertIn("terrain semantic payload contains unknown ordinal", source)
        self.assertIn("deriveTerrainSurfaces", source)
        self.assertIn("sliceAtYIndex", source)
        self.assertIn("unsupported artifact_kind", source)
        self.assertIn("unsupported atmosphere schema_version", source)
        self.assertIn("unsupported terrain schema_version", source)

        self.assertIn("TERRAIN_COLORS", app)
        self.assertIn("terrainDisplayPoints", app)
        self.assertIn("drawTerrain", app)
        self.assertIn('id="terrain-view"', markup)
        self.assertIn('id="terrain-slice"', markup)
        self.assertIn('id="terrain-surface"', markup)
        self.assertIn('id="hydrology-potential"', markup)
        self.assertIn('id="show-flow-vectors"', markup)
        self.assertIn('id="show-channel-width"', markup)
        self.assertIn('id="show-hydrology-response"', markup)
        self.assertIn('id="show-water-intent"', markup)
        self.assertIn('id="inspect-semantic"', markup)

        self.assertIn("adaptOverlayArtifact", source)
        self.assertIn("terrain semantic SHA does not match loaded terrain specimen", source)
        self.assertIn("hydrology grid does not match loaded terrain semantic specimen", source)
        self.assertIn("local/world coordinates do not match specimen transform", source)
        self.assertIn("hydrology cause sample count does not match grid_binding", source)
        self.assertIn("SkyIslandHydrologyField", source)
        self.assertIn("drawHydrologyOverlay", app)
        self.assertIn("runoffPotential", app)
        self.assertIn("bankfullHalfWidth", app)

        self.assertNotIn("Math.random", source)
        self.assertNotIn("fetch(", source)
        self.assertNotIn("localStorage", source)

    def test_console_links_to_studio_without_changing_console_authority(self):
        console = Path(hosted.__file__).resolve().parent / "console"
        markup = (console / "index.html").read_text(encoding="utf-8")
        self.assertIn('href="/studio"', markup)
        self.assertIn("Skyforge Studio", markup)


if __name__ == "__main__":
    unittest.main()
