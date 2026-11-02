"""Executable source/schema checks. These do NOT replace Android build or device tests."""
import json
from pathlib import Path
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/org/alquds/library"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def java_string(expression):
    return "".join(json.loads(token) for token in re.findall(r'"(?:[^"\\]|\\.)*"', expression))


class SchemaTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        source = (JAVA / "CatalogDatabase.java").read_text()
        schema = source.split("@Override public void onCreate", 1)[1].split("@Override public void onUpgrade", 1)[0]
        for expression in re.findall(r"db\.execSQL\((.*?)\);", schema, re.S):
            self.db.execute(java_string(expression))
        self.sql = java_string(re.search(r"String sql = (.*?);", source, re.S).group(1))
        self.db.execute("INSERT INTO books(id,title,author_id,category_id,search_text) VALUES(1,'القدس',1,1,'القدس')")

    def tearDown(self):
        self.db.close()

    def test_actual_search_sql_executes(self):
        self.assertEqual(len(self.db.execute(self.sql, ("%القدس%", "0")).fetchall()), 1)

    def test_sql_injection_is_data(self):
        self.assertEqual(self.db.execute(self.sql, ("%' OR 1=1 --%", "0")).fetchall(), [])

    def test_like_wildcard_escape(self):
        self.db.execute("UPDATE books SET search_text='100%_books'")
        self.assertEqual(len(self.db.execute(self.sql, (r"%100\%\_%", "0")).fetchall()), 1)
        self.assertEqual(self.db.execute(self.sql, (r"%100\%\_wrong%", "0")).fetchall(), [])

    def test_archived_books_are_hidden(self):
        self.db.execute("UPDATE books SET archived=1")
        self.assertEqual(self.db.execute(self.sql, ("%", "0")).fetchall(), [])

    def test_license_is_restricted(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE books SET license='unverified'")

    def test_author_foreign_key(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE books SET author_id=999")

    def test_category_foreign_key(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM categories WHERE id=1")

    def test_isbn_is_unique(self):
        self.db.execute("UPDATE books SET isbn='9780000000000'")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO books(title,author_id,category_id,search_text,isbn) VALUES('x',1,1,'x','9780000000000')")

    def test_result_limit(self):
        self.db.executemany("INSERT INTO books(title,author_id,category_id,search_text) VALUES(?,1,1,'القدس')",
                            [(str(i),) for i in range(120)])
        self.assertEqual(len(self.db.execute(self.sql, ("%", "0")).fetchall()), 100)

    def test_favorites_filter_and_flag(self):
        self.assertEqual(self.db.execute(self.sql, ("%", "1")).fetchall(), [])
        self.db.execute("INSERT INTO favorites VALUES(1)")
        self.assertEqual(self.db.execute(self.sql, ("%", "1")).fetchone()[6], 1)
        self.db.execute("DELETE FROM favorites")
        self.assertEqual(self.db.execute(self.sql, ("%", "0")).fetchone()[6], 0)

    def test_favorite_relationship_and_delete_cascade(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO favorites VALUES(999)")
        self.db.execute("INSERT INTO favorites VALUES(1)")
        self.db.execute("DELETE FROM books WHERE id=1")
        self.assertEqual(self.db.execute("SELECT * FROM favorites").fetchall(), [])

    def test_favorites_are_filtered_before_limit(self):
        self.db.executemany("INSERT INTO books(title,author_id,category_id,search_text) VALUES(?,1,1,'bulk')",
                            [(f"bulk {i:03}",) for i in range(120)])
        last = self.db.execute("SELECT MAX(id) FROM books").fetchone()[0]
        self.db.execute("INSERT INTO favorites VALUES(?)", (last,))
        self.assertEqual(len(self.db.execute(self.sql, ("%bulk%", "0")).fetchall()), 100)
        rows = self.db.execute(self.sql, ("%bulk%", "1")).fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0][0], last)


class RepositoryTests(unittest.TestCase):
    def test_manifest_targets_existing_activity(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
        app = manifest.getroot().find("application")
        self.assertEqual(app.attrib[ANDROID + "supportsRtl"], "true")
        self.assertEqual(app.attrib[ANDROID + "usesCleartextTraffic"], "false")
        self.assertEqual(app.attrib[ANDROID + "allowBackup"], "false")
        self.assertTrue((JAVA / (app.find("activity").attrib[ANDROID + "name"][1:] + ".java")).is_file())

    def test_resource_xml_is_well_formed(self):
        for path in (ROOT / "app/src/main/res").rglob("*.xml"):
            with self.subTest(path=path):
                ET.parse(path)

    def test_ci_includes_build_lint_and_unit_tests(self):
        ci = (ROOT / ".github/workflows/android-release-gate.yml").read_text()
        for command in ("testDebugUnitTest", "assembleDebug", "lintDebug", "unittest discover"):
            self.assertIn(command, ci)
        self.assertNotIn("com/nexvary/library", ci)


if __name__ == "__main__":
    unittest.main()
