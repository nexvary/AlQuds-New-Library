package org.alquds.library;

public final class Book {
    public final long id;
    public final String title, author, category, description, license;
    public final boolean favorite;

    public Book(long id, String title, String author, String category,
                String description, String license) {
        this(id, title, author, category, description, license, false);
    }

    public Book(long id, String title, String author, String category,
                String description, String license, boolean favorite) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.description = description;
        this.license = license;
        this.favorite = favorite;
    }
}
