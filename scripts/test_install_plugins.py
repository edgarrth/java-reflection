import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import install_plugins


class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "datasets").mkdir()
        (self.root / "alpha.jar").write_bytes(b"trusted-test-artifact")
        definitions = {"providers": [{"code": "ALPHA", "artifact": "alpha.jar", "className": "example.Alpha"},
                                     {"code": "BETA", "artifact": "missing.jar", "className": "example.Beta"}]}
        (self.root / "datasets/payment-plugins.json").write_text(json.dumps(definitions), encoding="utf-8")
        self.runtime = self.root / "runtime"
        patcher = patch.object(install_plugins, "ROOT", self.root)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_publishes_exact_hash_and_readable_files(self):
        catalog = install_plugins.install(self.runtime, ["ALPHA"])
        entry = json.loads(catalog.read_text(encoding="utf-8"))["plugins"][0]
        artifact = self.runtime / "plugins" / entry["jar"]
        self.assertEqual(entry["sha256"], hashlib.sha256(artifact.read_bytes()).hexdigest())
        if os.name != "nt":
            self.assertEqual(catalog.stat().st_mode & 0o777, 0o644)
            self.assertEqual(artifact.stat().st_mode & 0o777, 0o644)
        self.assertEqual(install_plugins.install(self.runtime, ["ALPHA"]), catalog)

    def test_failed_install_preserves_previous_catalog(self):
        catalog = install_plugins.install(self.runtime, ["ALPHA"])
        before = catalog.read_bytes()
        with self.assertRaises(FileNotFoundError):
            install_plugins.install(self.runtime, ["ALPHA", "BETA"])
        self.assertEqual(catalog.read_bytes(), before)
        self.assertEqual(list((self.runtime / "plugins").glob("*.tmp")), [])

    def test_refuses_tampered_existing_artifact(self):
        catalog = install_plugins.install(self.runtime, ["ALPHA"])
        entry = json.loads(catalog.read_text(encoding="utf-8"))["plugins"][0]
        (self.runtime / "plugins" / entry["jar"]).write_bytes(b"tampered")
        with self.assertRaises(ValueError):
            install_plugins.install(self.runtime, ["ALPHA"])


if __name__ == "__main__":
    unittest.main()
