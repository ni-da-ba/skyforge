import pathlib
import unittest

SCRIPT = pathlib.Path(__file__).with_name("decommission_hosted.sh")


class DecommissionContractTests(unittest.TestCase):
    def test_report_failure_does_not_abort_teardown(self):
        text = SCRIPT.read_text()
        self.assertIn("report_failed=1", text)
        self.assertIn("Teardown continued intentionally", text)
        self.assertNotIn("refusing teardown so the accounting boundary is not silently lost", text)

    def test_provider_destruction_remains_external(self):
        text = SCRIPT.read_text()
        self.assertIn("Destroy the DigitalOcean Droplet from the provider control plane.", text)
        self.assertNotIn("droplet_delete", text)


if __name__ == "__main__":
    unittest.main()
