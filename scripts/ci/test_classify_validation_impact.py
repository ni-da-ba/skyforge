import unittest

from classify_validation_impact import classify_paths


class ValidationImpactTest(unittest.TestCase):
    def test_workflow_only_is_lightweight(self):
        result = classify_paths([
            ".github/workflows/dr30-native-structure-acceptance.yml",
            ".github/workflows/ci.yml",
        ])
        self.assertFalse(result.ordinary)
        self.assertFalse(result.music)

    def test_ci_helpers_and_docs_are_lightweight(self):
        result = classify_paths([
            "scripts/ci/verify_validation_workflows.py",
            "config/ci/evidence-entry-points.json",
            "docs/agent-state/VALIDATION_POLICY.md",
        ])
        self.assertFalse(result.ordinary)

    def test_orchestrator_only_remains_lightweight(self):
        result = classify_paths([
            "scripts/orchestrator/v2/human_review.py",
            "deploy/orchestrator/skyforge-platform-v2.service",
        ])
        self.assertFalse(result.ordinary)

    def test_hydrology_routes_to_neoforge_and_dr50_not_aircraft_or_compiler(self):
        result = classify_paths([
            "skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211/"
            "SkyforgeAuthoredVisibleHydrologyAdapter.java",
        ])
        self.assertTrue(result.ordinary)
        self.assertTrue(result.neoforge)
        self.assertTrue(result.dr50)
        self.assertFalse(result.aircraft)
        self.assertFalse(result.compiler)
        self.assertFalse(result.reference)

    def test_shared_neoforge_build_script_gets_one_ordinary_gate_only(self):
        result = classify_paths(["skyforge-neoforge-1211/build.gradle.kts"])
        self.assertTrue(result.ordinary)
        self.assertTrue(result.neoforge)
        self.assertFalse(result.dr30)
        self.assertFalse(result.dr40)
        self.assertFalse(result.dr50)
        self.assertFalse(result.aircraft)
        self.assertFalse(result.compiler)

    def test_world_ecology_routes_reference_dr40_and_dr50(self):
        result = classify_paths([
            "skyforge-world/src/main/java/io/github/nidaba/skyforge/world/SkyIslandEcologyPlan.java",
        ])
        self.assertTrue(result.ordinary)
        self.assertTrue(result.reference)
        self.assertTrue(result.dr40)
        self.assertTrue(result.dr50)

    def test_aircraft_source_is_not_compiler_scope_without_shared_dependency(self):
        result = classify_paths([
            "skyforge-model/src/main/java/io/github/nidaba/skyforge/model/aircraft/AircraftState.java",
        ])
        self.assertTrue(result.ordinary)
        self.assertTrue(result.reference)
        self.assertTrue(result.aircraft)
        self.assertFalse(result.compiler)

    def test_reusable_java_gradle_action_fails_safe_to_ordinary_unknown(self):
        result = classify_paths([".github/actions/setup-java-gradle/action.yml"])
        self.assertTrue(result.ordinary)
        self.assertTrue(result.unknown)

    def test_music_change_uses_music_scope_without_product_gradle(self):
        result = classify_paths(["assets/music/theme/source.mid"])
        self.assertFalse(result.ordinary)
        self.assertTrue(result.music)

    def test_music_verifier_uses_music_scope_without_product_gradle(self):
        result = classify_paths(["scripts/music/verify_music_sources.py"])
        self.assertFalse(result.ordinary)
        self.assertTrue(result.music)

    def test_empty_change_set_fails_safe_to_all_scopes(self):
        result = classify_paths([])
        self.assertTrue(result.ordinary)
        self.assertTrue(result.reference)
        self.assertTrue(result.neoforge)
        self.assertTrue(result.dr50)
        self.assertTrue(result.aircraft)
        self.assertTrue(result.compiler)
        self.assertTrue(result.music)
        self.assertTrue(result.unknown)


if __name__ == "__main__":
    unittest.main()
