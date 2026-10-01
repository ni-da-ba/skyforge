import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("verify_main_protection.py")
SPEC = importlib.util.spec_from_file_location("verify_main_protection", MODULE_PATH)
assert SPEC and SPEC.loader
guard = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = guard
SPEC.loader.exec_module(guard)


class RulesetProtectionTests(unittest.TestCase):
    def test_required_ruleset_contract_passes(self):
        rules = [{"type": kind} for kind in guard.REQUIRED_RULESET_TYPES]
        ok, kinds = guard.ruleset_protection_ok(rules)
        self.assertTrue(ok)
        self.assertEqual(kinds, guard.REQUIRED_RULESET_TYPES)

    def test_missing_status_checks_fails(self):
        rules = [
            {"type": "pull_request"},
            {"type": "non_fast_forward"},
            {"type": "deletion"},
        ]
        ok, _ = guard.ruleset_protection_ok(rules)
        self.assertFalse(ok)

    def test_classic_contract_passes(self):
        value = {
            "required_pull_request_reviews": {"required_approving_review_count": 0},
            "required_status_checks": {"strict": True, "contexts": ["CI"]},
            "allow_force_pushes": {"enabled": False},
            "allow_deletions": {"enabled": False},
            "enforce_admins": {"enabled": True},
        }
        self.assertTrue(guard.classic_protection_ok(value))

    def test_classic_admin_bypass_fails(self):
        value = {
            "required_pull_request_reviews": {"required_approving_review_count": 0},
            "required_status_checks": {"strict": False, "contexts": ["build"]},
            "allow_force_pushes": {"enabled": False},
            "allow_deletions": {"enabled": False},
            "enforce_admins": {"enabled": False},
        }
        self.assertFalse(guard.classic_protection_ok(value))

    def test_classic_force_push_or_missing_checks_fails(self):
        value = {
            "required_pull_request_reviews": {},
            "required_status_checks": None,
            "allow_force_pushes": {"enabled": True},
            "allow_deletions": {"enabled": False},
            "enforce_admins": {"enabled": False},
        }
        self.assertFalse(guard.classic_protection_ok(value))


if __name__ == "__main__":
    unittest.main()
