import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("source_policy", Path(__file__).resolve().parents[1] / "tools/check_source_policy.py")
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class SourcePolicyTests(unittest.TestCase):
    def check(self, name, text):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            return policy.check_tree(root, [name])

    def test_known_token_is_rejected_without_echoing_it(self):
        token = "ghp_" + "A" * 36
        errors = self.check("config.txt", "credential=" + token)
        self.assertEqual(len(errors), 1)
        self.assertNotIn(token, errors[0])

    def test_private_key_is_rejected(self):
        self.assertTrue(self.check("config.txt", "-----BEGIN " + "RSA PRIVATE KEY-----"))

    def test_signing_files_are_rejected_even_if_empty(self):
        self.assertTrue(self.check("release.jks", ""))
        self.assertTrue(self.check("local.properties", ""))
        self.assertTrue(self.check(".env.production", ""))
        self.assertEqual(self.check(".env.example", "API_URL=https://example.org"), [])

    def test_dynamic_plugin_is_rejected(self):
        self.assertTrue(self.check("build.gradle", "id 'com.android.application' version '8.+'"))

    def test_dynamic_dependency_is_rejected(self):
        for version in ["1.+", "latest.release", "1.0-SNAPSHOT"]:
            with self.subTest(version=version):
                self.assertTrue(self.check("app/build.gradle", f"implementation 'group:library:{version}'"))

    def test_fixed_dependency_and_secure_repository_are_allowed(self):
        self.assertEqual(self.check("app/build.gradle", "implementation 'group:library:1.2.3'\nmaven { url 'https://example.org/maven' }"), [])

    def test_insecure_repository_is_rejected(self):
        self.assertTrue(self.check("build.gradle", "maven { url 'http://example.org/maven' }"))

    def test_xml_namespace_is_not_a_dependency_url(self):
        self.assertEqual(self.check("AndroidManifest.xml", 'xmlns:android="http://schemas.android.com/apk/res/android"'), [])
