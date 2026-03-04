package com.restaurant.admin.util;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class EmailUtilTest {

    // test that method returns null when input email is null
    @Test
    void normalize_nullInput_returnsNull() {
        assertNull(EmailUtil.normalize(null));
    }

    // test that method removes spaces and converts email to lowercase
    @Test
    void normalize_trimsAndLowercases() {
        assertEquals("test@example.com", EmailUtil.normalize("   TEST@Example.COM   "));
    }

    
    // test that lowercase conversion works correctly even if system locale is different
    @Test
    void normalize_usesLocaleRoot_notSystemDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            // Turkish locale is a classic case where lowercasing can behave differently for 'I'
            Locale.setDefault(new Locale("tr", "TR"));

            assertEquals("i@example.com", EmailUtil.normalize("I@EXAMPLE.COM"));
        } finally {
            Locale.setDefault(previous);
        }
    }
    
    // test that empty string stays empty after normalization
    @Test
    void normalize_emptyString() {
        assertEquals("", EmailUtil.normalize(""));
    }

    // test that string with only spaces becomes empty string
    @Test
    void normalize_spacesOnly() {
        assertEquals("", EmailUtil.normalize("   "));
    }

}
