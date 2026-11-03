package org.alquds.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run on a dedicated test device. Reads the catalog; restores the theme preference. */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    private SharedPreferences preferences;
    private boolean hadTheme, originalTheme;

    @Before public void rememberTheme() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("library", Context.MODE_PRIVATE);
        hadTheme = preferences.contains("dark");
        originalTheme = preferences.getBoolean("dark", false);
        assertTrue(preferences.edit().putBoolean("dark", false).commit());
    }

    @After public void restoreTheme() {
        SharedPreferences.Editor editor = preferences.edit();
        if (hadTheme) editor.putBoolean("dark", originalTheme);
        else editor.remove("dark");
        assertTrue(editor.commit());
    }

    private static void collect(View view, List<View> views) {
        views.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), views);
        }
    }

    private static List<View> views(MainActivity activity) {
        List<View> views = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), views);
        return views;
    }

    private static EditText search(MainActivity activity) {
        for (View view : views(activity)) if (view instanceof EditText) return (EditText) view;
        throw new AssertionError("Search input is missing");
    }

    private static Button button(MainActivity activity, String label) {
        for (View view : views(activity)) {
            if (view instanceof Button && ((Button) view).getText().toString().equals(label)) {
                return (Button) view;
            }
        }
        throw new AssertionError("Missing button: " + label);
    }

    private static void awaitText(ActivityScenario<MainActivity> scenario, String prefix) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicBoolean found = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                for (View view : views(activity)) {
                    if (view instanceof TextView
                            && ((TextView) view).getText().toString().startsWith(prefix)) found.set(true);
                }
            });
            if (found.get()) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for: " + prefix);
    }

    @Test public void recreationPreservesQueryAndFavoriteFilter() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                search(activity).setText("اختبار لا يوجد له سجل 781293");
                button(activity, "عرض مفضلتي على هذا الجهاز").performClick();
            });
            awaitText(scenario, "لا توجد نتائج.");
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("اختبار لا يوجد له سجل 781293", search(activity).getText().toString());
                assertNotNull(button(activity, "عرض جميع الكتب"));
            });
            awaitText(scenario, "لا توجد نتائج.");
        }
    }

    @Test public void themeSurvivesRecreationAndActivityRelaunch() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitText(scenario, "النتائج: ");
            captureScreen("stage11-light.png");
            scenario.onActivity(activity -> button(activity, "تفعيل الوضع الليلي").performClick());
            awaitText(scenario, "النتائج: ");
            captureScreen("stage11-dark.png");
            scenario.recreate();
            scenario.onActivity(activity -> assertNotNull(button(activity, "تفعيل الوضع النهاري")));
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertNotNull(button(activity, "تفعيل الوضع النهاري")));
        }
    }

    private static void captureScreen(String name) {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        android.graphics.Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Screenshot unavailable", bitmap);
        java.io.File directory = new java.io.File(instrumentation.getTargetContext()
                .getExternalFilesDir(null), "visual-review");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally {
            bitmap.recycle();
        }
    }

    @Test public void clearingMissingQueryRecoversCatalogAfterRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitText(scenario, "النتائج: ");
            scenario.onActivity(activity -> search(activity).setText("اختبار لا يوجد له سجل 781293"));
            awaitText(scenario, "لا توجد نتائج.");
            scenario.recreate();
            scenario.onActivity(activity -> search(activity).setText(""));
            awaitText(scenario, "النتائج: ");
        }
    }

    @Test public void realOrientationChangePreservesScreenState() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            awaitOrientation(scenario, Configuration.ORIENTATION_PORTRAIT);
            scenario.onActivity(activity -> {
                search(activity).setText("حالة محفوظة بعد الدوران");
                button(activity, "عرض مفضلتي على هذا الجهاز").performClick();
                button(activity, "تفعيل الوضع الليلي").performClick();
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            awaitOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE);
            scenario.onActivity(activity -> {
                assertEquals("حالة محفوظة بعد الدوران", search(activity).getText().toString());
                assertNotNull(button(activity, "عرض جميع الكتب"));
                assertNotNull(button(activity, "تفعيل الوضع النهاري"));
            });
            awaitText(scenario, "لا توجد نتائج.");
        }
    }

    private static void awaitOrientation(ActivityScenario<MainActivity> scenario, int orientation) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicBoolean matched = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> matched.set(
                    activity.getResources().getConfiguration().orientation == orientation));
            if (matched.get()) return;
            SystemClock.sleep(50);
        }
        fail("Device did not reach requested orientation");
    }

    @Test public void storageFailureShowsRetryAndPreservesCatalog() {
        LibraryApplication app = (LibraryApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        LibraryApplication.CatalogFactory original = app.catalogFactory;
        String name = "ui-fault-" + UUID.randomUUID() + ".db";
        CatalogDatabase isolated = new CatalogDatabase(app, name);
        long bookId = isolated.search("").get(0).id;
        AtomicBoolean storageUnavailable = new AtomicBoolean(true);
        java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
        app.catalogFactory = context -> new CatalogSource() {
            @Override public List<Book> search(String query) {
                return search(query, false);
            }
            @Override public List<Book> search(String query, boolean favoritesOnly) {
                if (storageUnavailable.get()) throw new SQLiteCantOpenDatabaseException("injected test failure");
                return isolated.search(query, favoritesOnly);
            }
            @Override public void setFavorite(long bookId, boolean favorite) { isolated.setFavorite(bookId, favorite); }
            @Override public void close() { isolated.close(); closed.countDown(); }
        };
        try {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitText(scenario, "تعذر فتح الفهرس. لم تُحذف بياناتك.");
                storageUnavailable.set(false);
                scenario.onActivity(activity -> button(activity, "إعادة المحاولة").performClick());
                awaitText(scenario, "النتائج: ");
                assertEquals(bookId, isolated.search("").get(0).id);
            }
            assertTrue("Catalog must close after queued reads", closed.await(10, java.util.concurrent.TimeUnit.SECONDS));
            try (CatalogDatabase reopened = new CatalogDatabase(app, name)) {
                assertEquals(bookId, reopened.search("").get(0).id);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } finally {
            app.catalogFactory = original;
            isolated.close();
            app.deleteDatabase(name);
        }
    }

    @Test public void favoriteCanBeAddedFilteredAndRemovedFromDetails() {
        exerciseFavoriteSave(false);
    }

    @Test public void failedFavoriteWriteKeepsStateAndAllowsAnotherAttempt() {
        exerciseFavoriteSave(true);
    }

    private void exerciseFavoriteSave(boolean failInitially) {
        LibraryApplication app = (LibraryApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        LibraryApplication.CatalogFactory original = app.catalogFactory;
        String name = "ui-favorite-" + UUID.randomUUID() + ".db";
        CatalogDatabase isolated = new CatalogDatabase(app, name);
        Book book = isolated.search("").get(0);
        String label = book.title + "\n" + book.author + "\n" + book.category;
        AtomicBoolean rejectWrites = new AtomicBoolean(failInitially);
        java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
        app.catalogFactory = context -> new CatalogSource() {
            @Override public List<Book> search(String query) { return isolated.search(query); }
            @Override public List<Book> search(String query, boolean favorites) { return isolated.search(query, favorites); }
            @Override public void setFavorite(long id, boolean favorite) {
                if (rejectWrites.get()) throw new SQLiteCantOpenDatabaseException("injected write failure");
                isolated.setFavorite(id, favorite);
            }
            @Override public void close() { isolated.close(); closed.countDown(); }
        };
        try {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitText(scenario, "النتائج: ");
                scenario.onActivity(activity -> button(activity, label).performClick());
                clickDialogButton("إضافة إلى المفضلة");
                if (failInitially) {
                    awaitText(scenario, "تعذر حفظ المفضلة.");
                    assertFalse(isolated.search("").get(0).favorite);
                    rejectWrites.set(false);
                    scenario.onActivity(activity -> button(activity, label).performClick());
                    clickDialogButton("إضافة إلى المفضلة");
                }
                awaitFavorite(isolated, true);
                awaitText(scenario, "النتائج: ");
                scenario.onActivity(activity -> button(activity, "عرض مفضلتي على هذا الجهاز").performClick());
                awaitText(scenario, "النتائج: ");
                scenario.recreate();
                awaitText(scenario, "النتائج: ");
                scenario.onActivity(activity -> button(activity, label).performClick());
                clickDialogButton("إزالة من المفضلة");
                awaitFavorite(isolated, false);
                awaitText(scenario, "لا توجد نتائج.");
            }
            assertTrue(closed.await(10, java.util.concurrent.TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } finally {
            app.catalogFactory = original;
            isolated.close();
            app.deleteDatabase(name);
        }
    }

    private static void awaitFavorite(CatalogDatabase database, boolean favorite) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (database.search("").get(0).favorite == favorite) return;
            SystemClock.sleep(50);
        }
        fail("Favorite did not reach expected state");
    }

    private static void clickDialogButton(String label) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        while (SystemClock.uptimeMillis() < deadline) {
            android.view.accessibility.AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                    if (label.contentEquals(node.getText() == null ? "" : node.getText())
                            && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                        instrumentation.waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(50);
        }
        fail("Dialog button was not actionable: " + label);
    }
}
