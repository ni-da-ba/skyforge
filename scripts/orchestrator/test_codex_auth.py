import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("codex_auth.py")
SPEC = importlib.util.spec_from_file_location("codex_auth", MODULE_PATH)
assert SPEC and SPEC.loader
auth = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = auth
SPEC.loader.exec_module(auth)


class FakeModel:
    def __init__(self, payload):
        self.payload = payload

    def model_dump(self):
        return self.payload


class AccountTests(unittest.TestCase):
    def test_account_present_for_model_payload(self):
        self.assertTrue(auth.account_present(FakeModel({"account": {"type": "chatgpt"}})))

    def test_account_missing_is_false(self):
        self.assertFalse(auth.account_present(FakeModel({"account": None})))

    def test_plain_mapping_is_supported(self):
        self.assertTrue(auth.account_present({"account": {"type": "chatgpt"}}))


if __name__ == "__main__":
    unittest.main()
