package org.alquds.library;

import android.content.Context;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Local catalog only. No network content, user accounts, or licensed book files. */
public final class CatalogDatabase extends SQLiteOpenHelper implements CatalogSource {
    static final int VERSION = 2;
    private final SharedPreferences legacyFavorites;

    public CatalogDatabase(Context context) {
        this(context, "alquds-catalog.db", context.getSharedPreferences("library", Context.MODE_PRIVATE));
    }

    // Package-private so instrumentation tests never open the user's catalog.
    CatalogDatabase(Context context, String databaseName) {
        this(context, databaseName, context.getSharedPreferences(databaseName + "-legacy", Context.MODE_PRIVATE));
    }

    CatalogDatabase(Context context, String databaseName, SharedPreferences legacyFavorites) {
        super(context, databaseName, null, VERSION);
        this.legacyFavorites = legacyFavorites;
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE authors (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, "
                + "author_id INTEGER NOT NULL REFERENCES authors(id), "
                + "category_id INTEGER NOT NULL REFERENCES categories(id), "
                + "description TEXT NOT NULL DEFAULT '', isbn TEXT UNIQUE, "
                + "license TEXT NOT NULL DEFAULT 'metadata_only' "
                + "CHECK(license IN ('metadata_only','public_domain','licensed')), "
                + "archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1)), "
                + "search_text TEXT NOT NULL)");
        db.execSQL("CREATE INDEX books_author ON books(author_id)");
        db.execSQL("CREATE INDEX books_category ON books(category_id, archived)");
        // An explicit, original demo record, not a fabricated published book.
        db.execSQL("INSERT INTO authors VALUES (1, 'فريق مكتبة القدس الجديدة')");
        db.execSQL("INSERT INTO categories VALUES (1, 'دليل الاستخدام')");
        ContentValues book = new ContentValues();
        book.put("title", "مرحبًا بك في مكتبة القدس الجديدة — سجل تجريبي");
        book.put("author_id", 1);
        book.put("category_id", 1);
        book.put("description", "هذا سجل توضيحي لا يمثل كتابًا منشورًا. ابحث بالعنوان أو المؤلف أو التصنيف. "
                + "ستُضاف الكتب بعد التحقق من بياناتها وحقوق إتاحتها. لا تتوفر ملفات للقراءة في هذه النسخة.");
        book.put("search_text", ArabicSearch.normalize(book.getAsString("title")
                + " فريق مكتبة القدس الجديدة دليل الاستخدام"));
        db.insertOrThrow("books", null, book);
        createVersionTwoTables(db);
    }

    private static void createVersionTwoTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE favorites (book_id INTEGER PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE local_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != 1 || newVersion != VERSION) {
            throw new IllegalStateException("Unsupported migration; existing data retained");
        }
        // SQLiteOpenHelper wraps this upgrade and user_version in one transaction.
        createVersionTwoTables(db);
    }

    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // A previously imported catalog remains readable without requiring another write transaction.
        try (Cursor marker = db.rawQuery("SELECT value FROM local_state WHERE key=?",
                new String[]{"favorites_imported_v1"})) {
            if (marker.moveToFirst()) return;
        }
        db.beginTransaction();
        try {
            try (Cursor marker = db.rawQuery("SELECT value FROM local_state WHERE key=?",
                    new String[]{"favorites_imported_v1"})) {
                if (marker.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return;
                }
            }
            for (Map.Entry<String, ?> entry : legacyFavorites.getAll().entrySet()) {
                if (!entry.getKey().startsWith("favorite.") || !Boolean.TRUE.equals(entry.getValue())) continue;
                try {
                    long id = Long.parseLong(entry.getKey().substring("favorite.".length()));
                    // Unknown/deleted book IDs are not imported; no orphan favorites.
                    db.execSQL("INSERT OR IGNORE INTO favorites(book_id) SELECT id FROM books WHERE id=?",
                            new Object[]{id});
                } catch (NumberFormatException ignored) { /* Invalid legacy key, not a book ID. */ }
            }
            db.execSQL("INSERT INTO local_state(key,value) VALUES('favorites_imported_v1','1')");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // Keep original preferences intact; the marker prevents re-importing removed favorites.
    }

    @Override
    public List<Book> search(String query) {
        return search(query, false);
    }

    @Override public List<Book> search(String query, boolean favoritesOnly) {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT b.id,b.title,a.name,c.name,b.description,b.license,f.book_id IS NOT NULL "
                + "FROM books b JOIN authors a ON a.id=b.author_id "
                + "JOIN categories c ON c.id=b.category_id "
                + "LEFT JOIN favorites f ON f.book_id=b.id "
                + "WHERE b.archived=0 AND b.search_text LIKE ? ESCAPE '\\' "
                + "AND (? = '0' OR f.book_id IS NOT NULL) "
                + "ORDER BY b.title COLLATE NOCASE, b.id LIMIT 100";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{ArabicSearch.likePattern(query), favoritesOnly ? "1" : "0"})) {
            while (cursor.moveToNext()) {
                books.add(new Book(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getInt(6) != 0));
            }
        }
        return books;
    }

    @Override public void setFavorite(long bookId, boolean favorite) {
        if (bookId <= 0) throw new IllegalArgumentException("Book ID must be positive");
        SQLiteDatabase db = getWritableDatabase();
        if (favorite) db.execSQL("INSERT OR IGNORE INTO favorites(book_id) VALUES(?)", new Object[]{bookId});
        else db.delete("favorites", "book_id=?", new String[]{Long.toString(bookId)});
    }
}
