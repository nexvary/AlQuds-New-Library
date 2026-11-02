package org.alquds.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Dedicated emulator only: run via tests/run_process_restart.py, never as a normal suite. */
@RunWith(AndroidJUnit4.class)
public class ProcessRestartProbe {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void prepare() {
        SharedPreferences settings = context().getSharedPreferences("library", Context.MODE_PRIVATE);
        SharedPreferences marker = context().getSharedPreferences("process-restart-probe", Context.MODE_PRIVATE);
        assertFalse("Unfinished probe; use a clean test emulator", marker.getBoolean("ready", false));
        long bookId;
        try (CatalogDatabase database = new CatalogDatabase(context())) {
            bookId = database.search("").get(0).id;
        }
        String key = "favorite." + bookId;
        assertTrue(marker.edit().putBoolean("hadTheme", settings.contains("dark"))
                .putBoolean("theme", settings.getBoolean("dark", false))
                .putBoolean("hadFavorite", settings.contains(key))
                .putBoolean("favorite", settings.getBoolean(key, false))
                .putLong("bookId", bookId).putInt("pid", android.os.Process.myPid())
                .putBoolean("ready", true).commit());
        assertTrue(settings.edit().putBoolean("dark", true).putBoolean(key, true).commit());
    }

    @Test public void verify() {
        SharedPreferences settings = context().getSharedPreferences("library", Context.MODE_PRIVATE);
        SharedPreferences marker = context().getSharedPreferences("process-restart-probe", Context.MODE_PRIVATE);
        assertTrue("Prepare must run in a previous process", marker.getBoolean("ready", false));
        String key = "favorite." + marker.getLong("bookId", -1);
        try {
            assertNotEquals("Must use a new Android process", marker.getInt("pid", -1), android.os.Process.myPid());
            assertTrue(settings.getBoolean("dark", false));
            assertTrue(settings.getBoolean(key, false));
            try (CatalogDatabase database = new CatalogDatabase(context())) {
                assertEquals(marker.getLong("bookId", -1), database.search("").get(0).id);
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> assertTrue(containsText(
                        activity.findViewById(android.R.id.content), "تفعيل الوضع النهاري")));
            }
        } finally {
            SharedPreferences.Editor restore = settings.edit();
            if (marker.getBoolean("hadTheme", false)) restore.putBoolean("dark", marker.getBoolean("theme", false));
            else restore.remove("dark");
            if (marker.getBoolean("hadFavorite", false)) restore.putBoolean(key, marker.getBoolean("favorite", false));
            else restore.remove(key);
            assertTrue(restore.commit());
            assertTrue(marker.edit().clear().commit());
        }
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
