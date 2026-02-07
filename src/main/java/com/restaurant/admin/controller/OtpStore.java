package com.restaurant.admin.controller;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class OtpStore {

    private static final Map<String, OtpData> otpMap = new HashMap<>();

    public static void saveOtp(String email, String otp) {
        otpMap.put(email,
            new OtpData(otp, LocalDateTime.now().plusMinutes(5))
        );
    }

    public static boolean verifyOtp(String email, String otp) {
    OtpData data = otpMap.get(email);

    if (data == null) return false;

    // Expired
    if (LocalDateTime.now().isAfter(data.getExpiry())) {
        otpMap.remove(email);
        return false;
    }

    return data.getOtp().equals(otp);
}


    public static void removeOtp(String email) {
        otpMap.remove(email);
    }
}