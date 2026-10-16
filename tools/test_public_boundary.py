#!/usr/bin/env python3
"""Regression checks for the explicit documentation-image publication boundary."""

from __future__ import annotations

import hashlib
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_public_boundary as boundary


class DocumentationBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.path = self.root / "docs/capture.png"
        self.path.parent.mkdir()
        self.path.write_bytes(b"reviewed-fixture")
        self.images = {"docs/capture.png": hashlib.sha256(self.path.read_bytes()).hexdigest()}
        self.root_patch = patch.object(boundary, "ROOT", self.root)
        self.images_patch = patch.object(boundary, "DOCUMENTATION_IMAGES", self.images)
        self.generated_patch = patch.object(boundary.generator, "generated_paths", return_value=[])
        self.check_patch = patch.object(boundary.generator, "check", return_value=[])
        for active in (self.root_patch, self.images_patch, self.generated_patch, self.check_patch):
            active.start()
            self.addCleanup(active.stop)

    def test_exact_documentation_capture_is_allowed(self):
        self.assertEqual(boundary.check_repository([]), [])

    def test_changed_documentation_capture_is_rejected(self):
        self.path.write_bytes(b"different-fixture")
        self.assertIn("documentation image differs from reviewed capture: docs/capture.png",
                      boundary.check_repository([]))

    def test_unlisted_image_is_rejected(self):
        (self.path.parent / "unreviewed.png").write_bytes(b"unreviewed-fixture")
        self.assertTrue(any("unreviewed.png" in item for item in boundary.check_repository([])))

    def test_missing_documentation_capture_is_rejected(self):
        self.path.unlink()
        self.assertIn("documentation image missing: docs/capture.png", boundary.check_repository([]))

    def test_documentation_capture_is_rejected_from_jar(self):
        jar = self.root / "fixture.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("docs/capture.png", self.path.read_bytes())
        self.assertIn("unexpected image in JAR: docs/capture.png", boundary.check_jar(jar, []))

    def test_restricted_name_is_still_rejected(self):
        self.assertIn("restricted term in path: docs/capture.png",
                      boundary.check_repository(["capture"]))


if __name__ == "__main__":
    unittest.main()
