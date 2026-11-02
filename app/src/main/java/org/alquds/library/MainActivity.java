package org.alquds.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CatalogDatabase database;
    private SharedPreferences preferences;
    private LinearLayout root, results;
    private TextView status;
    private EditText search;
    private boolean dark, favoritesOnly;
    private int generation;
    private int foreground, background, muted;
    private Runnable pendingSearch;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("library", MODE_PRIVATE);
        dark = preferences.getBoolean("dark", false);
        database = new CatalogDatabase(getApplicationContext());
        String query = state == null ? "" : state.getString("query", "");
        favoritesOnly = state != null && state.getBoolean("favorites");
        buildScreen(query);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String content, int size, int color) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        return view;
    }

    private Button button(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextColor(foreground);
        view.setMinHeight(dp(48));
        return view;
    }

    private void buildScreen(String query) {
        foreground = Color.parseColor(dark ? "#F4F0E6" : "#102E35");
        background = Color.parseColor(dark ? "#10232A" : "#FAF7F0");
        muted = Color.parseColor(dark ? "#D3CBB8" : "#4B5D5F");
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(background);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(dp(20) + bars.left, dp(16) + bars.top,
                        dp(20) + bars.right, dp(16) + bars.bottom);
            } else {
                view.setPadding(dp(20) + insets.getSystemWindowInsetLeft(),
                        dp(16) + insets.getSystemWindowInsetTop(),
                        dp(20) + insets.getSystemWindowInsetRight(),
                        dp(16) + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        scroll.addView(content);
        TextView title = text("مكتبة القدس الجديدة", 28, foreground);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        content.addView(title);
        content.addView(text("مساحة للمعرفة، ونافذة إلى القدس", 18, muted));
        content.addView(text("نسخة تأسيسية • فهرس محلي تجريبي", 14, muted));
        Button theme = button(dark ? "تفعيل الوضع النهاري" : "تفعيل الوضع الليلي");
        theme.setOnClickListener(v -> {
            dark = !dark;
            preferences.edit().putBoolean("dark", dark).apply();
            buildScreen(search.getText().toString());
        });
        content.addView(theme);
        TextView label = text("ابحث في الفهرس", 18, foreground);
        content.addView(label);
        search = new EditText(this);
        search.setId(View.generateViewId());
        label.setLabelFor(search.getId());
        search.setHint("عنوان، مؤلف، أو تصنيف");
        search.setTextColor(foreground);
        search.setHintTextColor(muted);
        search.setSingleLine(true);
        search.setMinHeight(dp(48));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        search.setText(query);
        content.addView(search);
        Button favorites = button(favoritesOnly ? "عرض جميع الكتب" : "عرض مفضلتي على هذا الجهاز");
        favorites.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            favorites.setText(favoritesOnly ? "عرض جميع الكتب" : "عرض مفضلتي على هذا الجهاز");
            loadBooks();
        });
        content.addView(favorites);
        status = text("جارٍ تحميل الفهرس…", 16, muted);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        content.addView(text("خصوصيتك أولًا: البحث والمفضلة محليان. لا تُرسل بيانات إلى خدمات خارجية.", 14, muted));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                generation++; // Invalidate results immediately, before the debounce finishes.
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> loadBooks();
                handler.postDelayed(pendingSearch, 250);
            }
            public void afterTextChanged(Editable value) {}
        });
        loadBooks();
    }

    private void loadBooks() {
        int request = ++generation;
        String query = search.getText().toString();
        status.setText("جارٍ البحث…");
        worker.execute(() -> {
            try {
                List<Book> books = database.search(query);
                handler.post(() -> {
                    if (isDestroyed() || request != generation) return;
                    results.removeAllViews();
                    int count = 0;
                    for (Book book : books) {
                        if (favoritesOnly && !preferences.getBoolean("favorite." + book.id, false)) continue;
                        count++;
                        Button card = button(book.title + "\n" + book.author + "\n" + book.category);
                        card.setAllCaps(false);
                        card.setPadding(dp(12), dp(16), dp(12), dp(16));
                        card.setOnClickListener(v -> showBook(book));
                        results.addView(card, new LinearLayout.LayoutParams(-1, -2));
                    }
                    status.setText(count == 0 ? "لا توجد نتائج. جرّب كلمات أخرى أو اعرض جميع الكتب."
                            : "النتائج: " + count + " — عرض حتى 100 سجل");
                });
            } catch (RuntimeException error) {
                handler.post(() -> {
                    if (isDestroyed() || request != generation) return;
                    results.removeAllViews();
                    status.setText("تعذر فتح الفهرس. لم تُحذف بياناتك.");
                    Button retry = button("إعادة المحاولة");
                    retry.setOnClickListener(v -> loadBooks());
                    results.addView(retry);
                });
            }
        });
    }

    private void showBook(Book book) {
        boolean favorite = preferences.getBoolean("favorite." + book.id, false);
        new AlertDialog.Builder(this).setTitle(book.title)
                .setMessage(book.author + "\n" + book.category + "\n\n" + book.description
                        + "\n\nالإتاحة: بيانات وصفية فقط، لا يوجد ملف للقراءة.")
                .setPositiveButton(favorite ? "إزالة من المفضلة" : "إضافة إلى المفضلة", (dialog, which) -> {
                    preferences.edit().putBoolean("favorite." + book.id, !favorite).apply();
                    loadBooks();
                }).setNegativeButton("إغلاق", null).show();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("query", search.getText().toString());
        out.putBoolean("favorites", favoritesOnly);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        // Close on the same queue after all reads have finished.
        try { worker.execute(() -> database.close()); }
        catch (RejectedExecutionException ignored) { /* Already shutting down. */ }
        worker.shutdown();
        super.onDestroy();
    }
}
