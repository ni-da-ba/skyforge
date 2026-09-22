import unittest

from classify_validation_impact import classify_paths


class ValidationImpactTest(unittest.TestCase):
    def test_workflow_only_is_lightweight_but_can_name_focused_scope(self):
        result = classify_paths([
            ".github/workflows/dr50-integrated-dressed-region.yml",
            ".github/workflows/ci.yml",
        ])
        self.assertFalse(result.full)
        self.assertTrue(result.dr50)
        self.assertFalse(result.reference)

    def test_ci_helpers_and_docs_are_lightweight(self):
        result = classify_paths([
            "scripts/ci/verify_validation_workflows.py",
            "config/ci/evidence-entry-points.json",
            "docs/agent-state/VALIDATION_POLICY.md",
        ])
        self.assertFalse(result.full)

    def test_orchestrator_only_remains_lightweight(self):
        result = classify_paths([
            "scripts/orchestrator/v2/human_review.py",
            "deploy/orchestrator/skyforge-platform-v2.service",
        ])
        self.assertFalse(result.full)

    def test_neoforge_hydrology_routes_ordinary_neoforge_without_reference_corpus(self):
        result = classify_paths([
            "skyforge-neoforge-1211/src/main/java/example/Hydrology.java",
        ])
        self.assertTrue(result.full)
        self.assertTrue(result.neoforge)
        self.assertFalse(result.reference)

    def test_reference_semantics_route_reference_corpus(self):
        result = classify_paths(["skyforge-world/src/main/java/example/World.java"])
        self.assertTrue(result.full)
        self.assertTrue(result.reference)

    def test_shared_build_change_fails_broadly_safe(self):
        result = classify_paths(["skyforge-neoforge-1211/build.gradle.kts"])
        self.assertTrue(result.full)
        self.assertTrue(result.neoforge)
        self.assertFalse(result.reference)

        root = classify_paths(["build.gradle.kts"])
        self.assertTrue(root.full)
        self.assertTrue(root.neoforge)
        self.assertTrue(root.reference)

    def test_aircraft_and_mechanism_scopes_are_distinct(self):
        aircraft = classify_paths(["docs/aircraft/rudder-contract.md"])
        mechanism = classify_paths([".github/workflows/mech-001-functional-mechanism.yml"])
        self.assertTrue(aircraft.aircraft)
        self.assertFalse(aircraft.compiler_mechanism)
        self.assertTrue(mechanism.compiler_mechanism)

    def test_music_change_sets_music_and_ordinary(self):
        result = classify_paths(["assets/music/theme/source.mid"])
        self.assertTrue(result.full)
        self.assertTrue(result.music)

    def test_unknown_change_set_fails_safe(self):
        result = classify_paths(["mystery-runtime/new-format.bin"])
        self.assertTrue(result.full)
        self.assertTrue(result.reference)
        self.assertTrue(result.neoforge)
        self.assertTrue(result.fail_safe)

    def test_empty_change_set_fails_safe(self):
        result = classify_paths([])
        self.assertTrue(result.full)
        self.assertTrue(result.reference)
        self.assertTrue(result.neoforge)
        self.assertTrue(result.fail_safe)


if __name__ == "__main__":
    unittest.main()
