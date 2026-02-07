package com.restaurant.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendOtp(String toEmail, String otp) {

        if (mailSender == null) {
            System.out.println("Mail sender not configured. Skipping email.");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your FlavorFrame OTP Code");
        message.setText("Your OTP code is: " + otp + "\n\nThis code expires in 5 minutes.");

        mailSender.send(message);
    }
}
