package org.alquds.library;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ArabicSearchTest {
    @Test public void foldsArabicVariants() {
        assertEquals("القدس الي اقصي", ArabicSearch.normalize(" الْقُدْس إِلَى أَقْصَى "));
    }
    @Test public void removesTatweelAndRepeatedWhitespace() {
        assertEquals("كتاب القدس", ArabicSearch.normalize("كـتاب  \n القدس"));
    }
    @Test public void escapesWildcardsAndBackslash() {
        assertEquals("%100\\%\\_\\\\%", ArabicSearch.likePattern("100%_\\"));
    }
    @Test public void acceptsNullAndLatin() {
        assertEquals("", ArabicSearch.normalize(null));
        assertEquals("isbn", ArabicSearch.normalize("ISBN"));
    }
    @Test public void treatsSqlAsLiteralData() {
        assertEquals("%' or 1=1 --%", ArabicSearch.likePattern("' OR 1=1 --"));
    }
}
