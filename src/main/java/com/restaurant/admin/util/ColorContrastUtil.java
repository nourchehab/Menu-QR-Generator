package com.restaurant.admin.util;

/**
 * Small WCAG contrast helper.
 *
 * We only need black/white text selection for a chosen background.
 * For any sRGB background color, either black or white will meet
 * the WCAG AA 4.5:1 contrast ratio for normal text.
 */
public final class ColorContrastUtil {
    private ColorContrastUtil() {}

    /**
     * Normalize a hex string into "#RRGGBB".
     * Accepts "#abc", "abc", "#aabbcc", "AABBCC".
     */
    public static String normalizeHex(String hex) {
        if (hex == null) return null;
        String s = hex.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("#")) s = s.substring(1);

        if (s.length() == 3) {
            char r = s.charAt(0);
            char g = s.charAt(1);
            char b = s.charAt(2);
            s = "" + r + r + g + g + b + b;
        }

        if (s.length() != 6 || !s.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid hex color. Use format #RRGGBB.");
        }
        return "#" + s.toUpperCase();
    }

    /** Return "#000000" or "#FFFFFF" depending on best contrast vs background. */
    public static String bestTextColor(String backgroundHex) {
        String bg = normalizeHex(backgroundHex);
        double L = relativeLuminance(bg);
        // Contrast ratios with black and white:
        double contrastBlack = (L + 0.05) / 0.05;
        double contrastWhite = (1.05) / (L + 0.05);
        return contrastBlack >= contrastWhite ? "#000000" : "#FFFFFF";
    }

    /** Contrast ratio between two hex colors ("#RRGGBB"). */
    public static double contrastRatio(String hex1, String hex2) {
        double L1 = relativeLuminance(normalizeHex(hex1));
        double L2 = relativeLuminance(normalizeHex(hex2));
        double lighter = Math.max(L1, L2);
        double darker = Math.min(L1, L2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        double RsRGB = r / 255.0;
        double GsRGB = g / 255.0;
        double BsRGB = b / 255.0;

        double R = (RsRGB <= 0.03928) ? (RsRGB / 12.92) : Math.pow((RsRGB + 0.055) / 1.055, 2.4);
        double G = (GsRGB <= 0.03928) ? (GsRGB / 12.92) : Math.pow((GsRGB + 0.055) / 1.055, 2.4);
        double B = (BsRGB <= 0.03928) ? (BsRGB / 12.92) : Math.pow((BsRGB + 0.055) / 1.055, 2.4);
        return 0.2126 * R + 0.7152 * G + 0.0722 * B;
    }
}
