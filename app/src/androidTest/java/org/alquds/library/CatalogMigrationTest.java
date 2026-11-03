package org.alquds.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A frozen v1 database fixture ensures the migration test cannot drift with v2 creation. */
@RunWith(AndroidJUnit4.class)
public class CatalogMigrationTest {
    private Context context;
    private String name;
    private SharedPreferences legacy;
    private CatalogDatabase helper;

    @Before public void prepareOldDatabase() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "migration-" + UUID.randomUUID() + ".db";
        legacy = context.getSharedPreferences(name + "-legacy", Context.MODE_PRIVATE);
        java.io.File directory = context.getDatabasePath(name).getParentFile();
        assertTrue(directory.isDirectory() || directory.mkdirs());
        String fixture;
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("catalog-v1.sql");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            fixture = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)) {
            old.execSQL("PRAGMA foreign_keys=ON");
            for (String statement : fixture.split(";")) if (!statement.trim().isEmpty()) old.execSQL(statement.trim());
            assertEquals(1, old.getVersion());
        }
        helper = new CatalogDatabase(context, name, legacy);
    }

    @After public void removeIsolatedFixture() {
        if (helper != null) helper.close();
        context.deleteDatabase(name);
        context.deleteSharedPreferences(name + "-legacy");
    }

    @Test public void upgradePreservesOriginalRowsAndMetadata() {
        assertEquals(CatalogDatabase.VERSION, helper.getReadableDatabase().getVersion());
        Book book = helper.search("").get(0);
        assertEquals(42, book.id);
        assertEquals("سجل قديم", book.title);
        assertEquals("مؤلف محفوظ", book.author);
        assertEquals("تصنيف محفوظ", book.category);
        assertEquals("وصف محفوظ", book.description);
        assertEquals("licensed", book.license);
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT isbn,archived FROM books ORDER BY id", null)) {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("9780000000000", cursor.getString(0));
            assertEquals(0, cursor.getInt(1));
            assertTrue(cursor.moveToNext());
            assertEquals(1, cursor.getInt(1));
        }
        try (Cursor check = helper.getReadableDatabase().rawQuery("PRAGMA foreign_key_check", null)) {
            assertEquals(0, check.getCount());
        }
    }

    @Test public void importsValidFavoritesOnceAndKeepsLegacyPreferences() {
        assertTrue(legacy.edit().putBoolean("favorite.42", true).putBoolean("favorite.43", true)
                .putBoolean("favorite.999", true).putBoolean("favorite.invalid", true)
                .putString("favorite.7", "true").putBoolean("dark", true).commit());
        assertEquals(1, helper.search("", true).size());
        assertEquals(42, helper.search("", true).get(0).id);
        assertTrue(helper.search("").get(0).favorite);
        try (Cursor favorites = helper.getReadableDatabase().rawQuery("SELECT book_id FROM favorites", null)) {
            assertEquals(2, favorites.getCount()); // Includes archived 43; not unknown 999 or malformed values.
        }
        assertTrue(legacy.getBoolean("favorite.42", false));
        assertTrue(legacy.getBoolean("dark", false));
        helper.setFavorite(42, false);
        helper.close();
        helper = new CatalogDatabase(context, name, legacy);
        assertTrue(helper.search("", true).isEmpty()); // Old true must not resurrect a removed favorite.
        assertFalse(helper.search("").get(0).favorite);
    }

    @Test public void falseLegacyFavoriteIsNotImported() {
        assertTrue(legacy.edit().putBoolean("favorite.42", false).commit());
        assertTrue(helper.search("", true).isEmpty());
    }

    @Test public void failedUpgradeRollsBackSchemaAndVersionWithoutDeletingRows() {
        try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)) {
            old.execSQL("CREATE TABLE local_state (conflicting_column TEXT)");
        }
        try {
            helper.getWritableDatabase();
            fail("A schema conflict must fail closed");
        } catch (SQLiteException expected) { /* No drop/recreate fallback. */ }
        try (SQLiteDatabase raw = SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor rows = raw.rawQuery("SELECT id FROM books ORDER BY id", null);
             Cursor tables = raw.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='favorites'", null)) {
            assertEquals(1, raw.getVersion());
            assertEquals(2, rows.getCount());
            assertTrue(rows.moveToFirst());
            assertEquals(42, rows.getLong(0));
            assertEquals(0, tables.getCount()); // The first CREATE was rolled back when the second failed.
        }
    }
}
