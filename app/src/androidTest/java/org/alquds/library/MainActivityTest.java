package org.alquds.library;

import android.content.Context;
import android.content.SharedPreferences;
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
            scenario.onActivity(activity -> button(activity, "تفعيل الوضع الليلي").performClick());
            scenario.recreate();
            scenario.onActivity(activity -> assertNotNull(button(activity, "تفعيل الوضع النهاري")));
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertNotNull(button(activity, "تفعيل الوضع النهاري")));
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
}
