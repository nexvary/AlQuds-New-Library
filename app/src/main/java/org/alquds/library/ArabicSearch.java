package org.alquds.library;

import java.text.Normalizer;
import java.util.Locale;

/** Shared normalization for user queries and indexed metadata. */
public final class ArabicSearch {
    private ArabicSearch() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{M}ـ]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ٱ', 'ا').replace('ى', 'ي')
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Escape LIKE syntax so user input remains literal, including percent and underscore. */
    public static String likePattern(String value) {
        return "%" + normalize(value).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
