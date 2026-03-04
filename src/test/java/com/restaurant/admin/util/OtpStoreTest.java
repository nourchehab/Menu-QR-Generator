package com.restaurant.admin.util;

import com.restaurant.admin.controller.OtpData;
import com.restaurant.admin.service.OtpStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OtpStoreTest {

    private static final String EMAIL = "test@example.com";
    private static final String OTP = "123456";

    @AfterEach
    void cleanup() throws Exception {
        OtpStore.removeOtp(EMAIL);
        // ensure the internal map does not retain test data
        Field f = OtpStore.class.getDeclaredField("otpMap");
        f.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) f.get(null);
        map.remove(EMAIL);
    }

    @Test
    void verifyOtp_noEntry_returnsFalse() {
        assertFalse(OtpStore.verifyOtp("noone@example.com", "000000"));
    }

    @Test
    void saveAndVerify_correctOtp_returnsTrue() {
        OtpStore.saveOtp(EMAIL, OTP);
        assertTrue(OtpStore.verifyOtp(EMAIL, OTP));
    }

    @Test
    void saveAndVerify_wrongOtp_returnsFalse() {
        OtpStore.saveOtp(EMAIL, OTP);
        assertFalse(OtpStore.verifyOtp(EMAIL, "000000"));
    }

    @Test
    void verifyOtp_expired_removesAndReturnsFalse() throws Exception {
        OtpStore.saveOtp(EMAIL, OTP);

        // set the stored expiry to the past using reflection
        Field f = OtpStore.class.getDeclaredField("otpMap");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, OtpData> map = (Map<String, OtpData>) f.get(null);
        map.put(EMAIL, new OtpData(OTP, LocalDateTime.now().minusMinutes(1)));

        assertFalse(OtpStore.verifyOtp(EMAIL, OTP));
        assertFalse(map.containsKey(EMAIL));
    }

    @Test
    void removeOtp_clearsEntry() {
        OtpStore.saveOtp(EMAIL, OTP);
        OtpStore.removeOtp(EMAIL);
        assertFalse(OtpStore.verifyOtp(EMAIL, OTP));
    }
}
