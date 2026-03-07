package com.restaurant.admin.util;

import java.util.Locale;

public class EmailUtil {
    private EmailUtil() {}

    public static String normalize(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
