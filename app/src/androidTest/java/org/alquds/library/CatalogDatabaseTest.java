package org.alquds.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises SQLiteOpenHelper and the actual query/normalizer on Android. */
@RunWith(AndroidJUnit4.class)
public class CatalogDatabaseTest {
    private Context context;
    private String name;
    private CatalogDatabase helper;

    @Before public void createIsolatedCatalog() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "catalog-test-" + UUID.randomUUID() + ".db";
        helper = new CatalogDatabase(context, name);
    }

    @After public void deleteOnlyTestCatalog() {
        helper.close();
        context.deleteDatabase(name);
    }

    private long addBook(String title, String author, String category) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", author);
        long authorId = db.insertOrThrow("authors", null, values);
        values.put("name", category);
        long categoryId = db.insertOrThrow("categories", null, values);
        values.clear();
        values.put("title", title);
        values.put("author_id", authorId);
        values.put("category_id", categoryId);
        values.put("description", "سجل اختبار محلي");
        values.put("search_text", ArabicSearch.normalize(title + " " + author + " " + category));
        return db.insertOrThrow("books", null, values);
    }

    private void assertConstraint(String sql) {
        try {
            helper.getWritableDatabase().execSQL(sql);
            fail("Expected database constraint rejection");
        } catch (SQLiteConstraintException expected) {
            // Expected: Android itself enforces the constraint.
        }
    }

    @Test public void createsSchemaAndOneExplicitDemoRecord() {
        assertEquals(1, helper.search("").size());
        Book demo = helper.search("").get(0);
        assertTrue(demo.title.contains("سجل تجريبي"));
        assertEquals("metadata_only", demo.license);
        assertEquals(1, helper.getReadableDatabase().getVersion());
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("PRAGMA foreign_keys", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
    }

    @Test public void reopeningPreservesRecordsAndDoesNotReseed() {
        long id = addBook("كتاب اختبار", "مؤلف اختبار", "تصنيف اختبار");
        helper.getWritableDatabase().execSQL("UPDATE books SET archived=1 WHERE id=1");
        helper.close();
        helper = new CatalogDatabase(context, name);
        assertEquals(1, helper.search("").size());
        assertEquals(id, helper.search("كتاب اختبار").get(0).id);
    }

    @Test public void rejectsOrphanAuthorsAndCategories() {
        assertConstraint("UPDATE books SET author_id=999 WHERE id=1");
        assertConstraint("UPDATE books SET category_id=999 WHERE id=1");
        assertConstraint("DELETE FROM authors WHERE id=1");
        assertConstraint("DELETE FROM categories WHERE id=1");
        assertEquals(1, helper.search("").size());
    }

    @Test public void restrictsLicenseArchiveAndRequiredTitle() {
        assertConstraint("UPDATE books SET license='unverified'");
        assertConstraint("UPDATE books SET archived=2");
        assertConstraint("UPDATE books SET title=NULL");
    }

    @Test public void rejectsDuplicateIsbnButAllowsMissingIsbn() {
        addBook("كتاب ثان", "مؤلف ثان", "تصنيف ثان");
        helper.getWritableDatabase().execSQL("UPDATE books SET isbn='9780000000000' WHERE id=1");
        assertConstraint("UPDATE books SET isbn='9780000000000' WHERE id<>1");
        assertEquals(2, helper.search("").size());
    }

    @Test public void archivedBooksAreHidden() {
        helper.getWritableDatabase().execSQL("UPDATE books SET archived=1");
        assertTrue(helper.search("").isEmpty());
        assertTrue(helper.search("القدس").isEmpty());
    }

    @Test public void arabicTitleAuthorAndCategoryUseSameNormalization() {
        long id = addBook("إِلَى الْقُدْسِ", "آمـال الاختبار", "تَارِيخ الاختبار");
        for (String query : new String[]{"الي القدس", "  إِلَى   الْقُدْس  ", "امال", "تاريخ"}) {
            assertEquals(query, id, helper.search(query).get(0).id);
        }
    }

    @Test public void percentUnderscoreAndBackslashRemainLiteral() {
        long id = addBook("100%_\\ خاص", "مؤلف رموز", "تصنيف رموز");
        assertEquals(1, helper.search("%_\\").size());
        assertEquals(id, helper.search("%_\\").get(0).id);
        assertTrue(helper.search("100%_\\wrong").isEmpty());
    }

    @Test public void injectionAndMissingQueriesReturnNoRecords() {
        assertTrue(helper.search("' OR 1=1 --").isEmpty());
        assertTrue(helper.search("عنوان غير موجود").isEmpty());
        assertEquals(helper.search("").size(), helper.search(null).size());
    }

    @Test public void limitsResultsAndOrdersEqualTitlesById() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < 120; i++) {
                db.execSQL("INSERT INTO books(title,author_id,category_id,search_text) VALUES(?,1,1,?)",
                        new Object[]{"متطابق", "متطابق"});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        java.util.List<Book> books = helper.search("متطابق");
        assertEquals(100, books.size());
        for (int i = 1; i < books.size(); i++) assertTrue(books.get(i).id > books.get(i - 1).id);
    }

    @Test public void unsupportedDowngradeFailsWithoutDeletingData() {
        long id = addBook("محفوظ", "مؤلف محفوظ", "تصنيف محفوظ");
        helper.getWritableDatabase().setVersion(2);
        helper.close();
        helper = new CatalogDatabase(context, name);
        try {
            helper.getReadableDatabase();
            fail("Unsupported downgrade must fail closed");
        } catch (SQLiteException expected) {
            // Do not delete or silently rebuild a newer catalog.
        }
        try (SQLiteDatabase raw = SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = raw.rawQuery("SELECT title FROM books WHERE id=?", new String[]{Long.toString(id)})) {
            assertEquals(2, raw.getVersion());
            assertTrue(cursor.moveToFirst());
            assertEquals("محفوظ", cursor.getString(0));
        }
    }
}
