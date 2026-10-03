import tempfile
from pathlib import Path
import unittest

from verify_validation_workflows import CONTRACTS, verify_text


class ValidationWorkflowContractTest(unittest.TestCase):
    def test_current_contract_shapes_have_expected_invariants(self):
        for contract in CONTRACTS:
            text = "\n".join(
                [
                    *contract.required_triggers,
                    *([ "uses: ./.github/actions/setup-java-gradle" ] * contract.setup_count),
                    *contract.acceptance_tasks,
                    *(
                        ["run: ./gradlew check -x :skyforge-neoforge-1211:test"]
                        if contract.path.endswith("studio-desktop.yml")
                        else []
                    ),
                ]
            )
            self.assertEqual(verify_text(contract, text), [])

    def test_self_trigger_is_rejected(self):
        contract = CONTRACTS[0]
        text = "\n".join(
            [
                *contract.required_triggers,
                contract.forbidden_trigger,
                "uses: ./.github/actions/setup-java-gradle",
                *contract.acceptance_tasks,
            ]
        )
        self.assertTrue(any("self-trigger" in item for item in verify_text(contract, text)))

    def test_duplicate_unit_suite_is_rejected(self):
        contract = CONTRACTS[1]
        text = "\n".join(
            [
                *contract.required_triggers,
                "uses: ./.github/actions/setup-java-gradle",
                *contract.acceptance_tasks,
                "./gradlew :skyforge-neoforge-1211:test",
            ]
        )
        self.assertTrue(any("duplicate canonical" in item for item in verify_text(contract, text)))

    def test_studio_does_not_run_for_unrelated_module_source_changes(self):
        contract = next(
            contract for contract in CONTRACTS if contract.path.endswith("studio-desktop.yml")
        )
        text = "\n".join(
            [
                *contract.required_triggers,
                "uses: ./.github/actions/setup-java-gradle",
                *contract.acceptance_tasks,
                "run: ./gradlew check -x :skyforge-neoforge-1211:test",
            ]
        )
        self.assertEqual(verify_text(contract, text), [])
        text += '\n- "skyforge-*/src/**"'
        self.assertTrue(
            any("unrelated module source/build changes" in item for item in verify_text(contract, text))
        )
    def test_studio_stage_requires_omitting_canonical_neoforge_unit_suite(self):
        contract = next(
            contract for contract in CONTRACTS if contract.path.endswith("studio-desktop.yml")
        )
        prefix = [
            *contract.required_triggers,
            "uses: ./.github/actions/setup-java-gradle",
            *contract.acceptance_tasks,
            "run: ./gradlew check",
        ]
        missing_exclusion = "\\n".join(prefix)
        self.assertTrue(
            any("must omit the duplicate canonical NeoForge unit suite" in item
                for item in verify_text(contract, missing_exclusion))
        )
        retained_check = "\\n".join(
            [*prefix, "run: ./gradlew check -x :skyforge-neoforge-1211:test"]
        )
        self.assertEqual(verify_text(contract, retained_check), [])

    def test_missing_product_trigger_is_rejected(self):
        contract = CONTRACTS[2]
        text = "\n".join(
            [
                *(entry for entry in contract.required_triggers if "skyforge-world" not in entry),
                *(["uses: ./.github/actions/setup-java-gradle"] * contract.setup_count),
                *contract.acceptance_tasks,
            ]
        )
        self.assertTrue(any("missing required trigger" in item for item in verify_text(contract, text)))


if __name__ == "__main__":
    unittest.main()
