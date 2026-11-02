package org.alquds.library;

import android.app.Application;
import android.content.Context;

/** Owns catalog creation, keeping storage construction outside the screen. */
public final class LibraryApplication extends Application {
    interface CatalogFactory { CatalogSource open(Context context); }
    // Package scope allows instrumented fault tests to use isolated storage.
    CatalogFactory catalogFactory = CatalogDatabase::new;

    CatalogSource openCatalog() { return catalogFactory.open(this); }
}
