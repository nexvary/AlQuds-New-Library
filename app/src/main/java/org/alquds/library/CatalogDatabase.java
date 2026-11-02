package org.alquds.library;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Local catalog only. No network content, user accounts, or licensed book files. */
public final class CatalogDatabase extends SQLiteOpenHelper implements CatalogSource {
    public CatalogDatabase(Context context) {
        this(context, "alquds-catalog.db");
    }

    // Package-private so instrumentation tests never open the user's catalog.
    CatalogDatabase(Context context, String databaseName) {
        super(context, databaseName, null, 1);
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
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("A non-destructive migration is required");
    }

    public List<Book> search(String query) {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT b.id,b.title,a.name,c.name,b.description,b.license "
                + "FROM books b JOIN authors a ON a.id=b.author_id "
                + "JOIN categories c ON c.id=b.category_id "
                + "WHERE b.archived=0 AND b.search_text LIKE ? ESCAPE '\\' "
                + "ORDER BY b.title COLLATE NOCASE, b.id LIMIT 100";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{ArabicSearch.likePattern(query)})) {
            while (cursor.moveToNext()) {
                books.add(new Book(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5)));
            }
        }
        return books;
    }
}
