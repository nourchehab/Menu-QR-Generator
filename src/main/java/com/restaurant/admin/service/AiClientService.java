package com.restaurant.admin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class AiClientService {

    private final String endpoint;
    private final String apiKey;
    private final RestTemplate rest;
    private final Logger log = LoggerFactory.getLogger(AiClientService.class);

    public AiClientService(@Value("${app.ai.endpoint:}") String endpoint,
                           @Value("${app.ai.apiKey:}") String apiKey) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey   = apiKey   == null ? "" : apiKey.trim();
        this.rest = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.rest.setRequestFactory(factory);
    }

    // Groq and any OpenAI-compatible endpoint uses /chat/completions
    private boolean isChatEndpoint() {
        return endpoint.contains("/chat/completions");
    }

    /**
     * Send a message to the configured AI endpoint.
     * Supports Groq (OpenAI-compatible) and HuggingFace legacy format.
     */
    public Optional<String> sendMessage(String message, List<String> history) {
        String userMessage = message == null ? "" : message;
        String fallback = buildFallback(userMessage);

        if (endpoint.isBlank() || apiKey.isBlank()) {
            log.warn("AI endpoint or API key not configured");
            return Optional.of(fallback);
        }

        log.info("Calling AI endpoint: {}", endpoint);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Object payload;

            if (isChatEndpoint()) {
                // ── Groq / OpenAI-compatible chat/completions ──
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", "You are a helpful restaurant menu assistant. Be concise and specific.");

                Map<String, Object> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userMessage);

                Map<String, Object> body = new HashMap<>();
                // llama-3.3-70b-versatile: best quality on Groq free tier
                body.put("model", "llama-3.3-70b-versatile");
                body.put("messages", List.of(systemMsg, userMsg));
                body.put("max_tokens", 500);
                body.put("temperature", 0.8);
                body.put("stream", false);
                payload = body;

            } else {
                // ── HuggingFace legacy text-generation (kept for compatibility) ──
                String prompt = "<s>[INST] You are a helpful restaurant menu assistant. "
                        + "Answer this: " + userMessage + " [/INST]";

                Map<String, Object> params = new HashMap<>();
                params.put("max_new_tokens", 300);
                params.put("temperature", 0.7);
                params.put("return_full_text", false);

                Map<String, Object> body = new HashMap<>();
                body.put("inputs", prompt);
                body.put("parameters", params);
                payload = body;
            }

            HttpEntity<Object> req = new HttpEntity<>(payload, headers);
            ResponseEntity<Object> resp = rest.postForEntity(endpoint, req, Object.class);
            HttpStatusCode status = resp.getStatusCode();
            Object body = resp.getBody();

            if (!status.is2xxSuccessful() || body == null) {
                log.warn("AI returned non-2xx: {} body={}", status.value(), body);
                return Optional.of(fallback);
            }

            String generated = extractText(body);

            if (generated == null || generated.isBlank()) {
                log.warn("AI returned empty text. Raw body: {}", body);
                return Optional.of(fallback);
            }

            log.info("AI returned {} chars", generated.length());
            return Optional.of(generated.trim());

        } catch (HttpStatusCodeException hsce) {
            log.warn("AI request failed: status={} body={}",
                    hsce.getStatusCode().value(), hsce.getResponseBodyAsString());
            return Optional.of(fallback);
        } catch (Exception e) {
            log.error("AI request error: {}", e.getMessage());
            return Optional.of(fallback);
        }
    }

    /**
     * Extract text from Groq/OpenAI or HuggingFace response shapes.
     */
    private String extractText(Object body) {

        // ── Groq / OpenAI: { choices: [{ message: { content: "..." } }] } ──
        if (body instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) body;

            Object choices = map.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object c0 = ((List<?>) choices).get(0);
                if (c0 instanceof Map) {
                    Map<?, ?> choice = (Map<?, ?>) c0;

                    // chat completions: message.content
                    Object msg = choice.get("message");
                    if (msg instanceof Map) {
                        Object content = ((Map<?, ?>) msg).get("content");
                        if (content != null) return content.toString();
                    }

                    // text completions: text
                    Object text = choice.get("text");
                    if (text != null) return text.toString();
                }
            }

            // generic fallback keys
            for (String key : new String[]{"reply", "text", "output", "generated_text"}) {
                Object val = map.get(key);
                if (val != null) return val.toString();
            }
        }

        // ── HuggingFace legacy: [{"generated_text": "..."}] ──
        if (body instanceof List) {
            List<?> list = (List<?>) body;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map) {
                    Object gen = ((Map<?, ?>) first).get("generated_text");
                    if (gen != null) return gen.toString();
                }
                if (first instanceof String) return (String) first;
            }
        }

        return null;
    }

    private String buildFallback(String userMessage) {
        return "You asked: \"" + userMessage + "\". AI is currently unavailable. "
                + "Try selecting from: Starters, Main Course, Drinks, Desserts, or Other.";
    }

    public Optional<String> sendMessage(String message) {
        return sendMessage(message, List.of());
    }

    public String chat(String userMessage) {
        return sendMessage(userMessage, List.of())
                .orElse(buildFallback(userMessage == null ? "" : userMessage));
    }
}