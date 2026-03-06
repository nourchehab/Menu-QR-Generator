package com.restaurant.admin.controller;
import java.time.LocalDateTime;

public class OtpData {

    private String otp;
    private LocalDateTime expiry;

    public OtpData(String otp, LocalDateTime expiry) {
        this.otp = otp;
        this.expiry = expiry;
    }

    public String getOtp() {
        return otp;
    }

    public LocalDateTime getExpiry() {
        return expiry;
    }
}