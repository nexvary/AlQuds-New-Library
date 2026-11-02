package org.alquds.library;

public final class Book {
    public final long id;
    public final String title, author, category, description, license;

    public Book(long id, String title, String author, String category,
                String description, String license) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.description = description;
        this.license = license;
    }
}
