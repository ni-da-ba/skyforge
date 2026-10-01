import unittest

from classify_validation_impact import classify_paths


class ValidationImpactTest(unittest.TestCase):
    def test_workflow_only_is_lightweight(self):
        result = classify_paths([
            ".github/workflows/dr30-native-structure-acceptance.yml",
            ".github/workflows/ci.yml",
        ])
        self.assertFalse(result.full)
        self.assertFalse(result.music)

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

    def test_product_source_requires_full(self):
        result = classify_paths([
            "skyforge-neoforge-1211/src/main/java/example/Foo.java",
        ])
        self.assertTrue(result.full)

    def test_reusable_java_gradle_action_requires_full(self):
        result = classify_paths([
            ".github/actions/setup-java-gradle/action.yml",
        ])
        self.assertTrue(result.full)

    def test_mixed_lightweight_and_product_requires_full(self):
        result = classify_paths([
            ".github/workflows/dr50-integrated-dressed-region.yml",
            "skyforge-world/src/main/java/example/World.java",
        ])
        self.assertTrue(result.full)

    def test_music_change_sets_music_and_full(self):
        result = classify_paths(["assets/music/theme/source.mid"])
        self.assertTrue(result.full)
        self.assertTrue(result.music)

    def test_music_verifier_sets_music_and_full(self):
        result = classify_paths(["scripts/music/verify_music_sources.py"])
        self.assertTrue(result.full)
        self.assertTrue(result.music)

    def test_empty_change_set_fails_safe(self):
        result = classify_paths([])
        self.assertTrue(result.full)


if __name__ == "__main__":
    unittest.main()
