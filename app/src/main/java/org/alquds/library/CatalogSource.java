package org.alquds.library;

import java.util.List;

/** A catalog opened for one screen; its owner closes it after pending reads. */
interface CatalogSource extends AutoCloseable {
    List<Book> search(String query);
    List<Book> search(String query, boolean favoritesOnly);
    void setFavorite(long bookId, boolean favorite);
    @Override void close();
}
