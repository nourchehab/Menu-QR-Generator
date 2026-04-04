package com.restaurant.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:}")
    private String brevoSenderEmail;

    @Value("${brevo.sender.name:FlavorFrame}")
    private String brevoSenderName;

    @Value("${brevo.api.url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Value("${brevo.timeout.seconds:20}")
    private int brevoTimeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public void sendOtp(String toEmail, String otp) {
        try {
            sendViaBrevo(
                    toEmail,
                    "Your FlavorFrame OTP Code",
                    "Your OTP code is: " + otp + "\n\nThis code expires in 5 minutes."
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private void sendViaBrevo(String toEmail, String subject, String textContent) throws Exception {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY is missing");
        }
        if (brevoSenderEmail == null || brevoSenderEmail.isBlank()) {
            throw new IllegalStateException("BREVO_SENDER_EMAIL is missing");
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", brevoSenderName, "email", brevoSenderEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "textContent", textContent
        );

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(brevoApiUrl))
                .timeout(Duration.ofSeconds(brevoTimeoutSeconds))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("api-key", brevoApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("Brevo API send failed with status " + status + ": " + response.body());
        }
    }
}
