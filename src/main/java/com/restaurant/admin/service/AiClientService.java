package com.restaurant.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
public class AiClientService {

    private final String endpoint;
    private final String apiKey;
    private final RestTemplate rest;
    private final Logger log = LoggerFactory.getLogger(AiClientService.class);

    public AiClientService(@Value("${app.ai.endpoint:}") String endpoint,
                           @Value("${app.ai.apiKey:}") String apiKey) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.rest = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.rest.setRequestFactory(factory);
    }

    /**
     * Send a message to the AI endpoint with optional conversation history.
     * Uses Mistral instruct format and includes parameters for generation.
     *
     * @param message the current user message
     * @param history prior turns, not used directly in instruct prompt here
     * @return the AI response or a dynamic fallback using the user message
     */
    public Optional<String> sendMessage(String message, List<String> history) {
        String userMessage = message == null ? "" : message;
        String fallback = "You asked: \"" + userMessage + "\". AI is currently unavailable. " +
                "Try selecting from: Starters, Main Course, Drinks, Desserts, or Other.";

        if (endpoint.isBlank() || apiKey.isBlank()) {
            log.warn("AI endpoint or API key not configured, returning fallback for message='{}'", userMessage);
            return Optional.of(fallback);
        }

        // Mistral instruct-style prompt
        String prompt = "<s>[INST] You are a helpful restaurant menu assistant. " +
                "Answer this: " + userMessage + " [/INST]";

        log.info("Calling AI with message: {}", userMessage);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> params = new HashMap<>();
            params.put("max_new_tokens", 200);
            params.put("temperature", 0.7);
            params.put("return_full_text", false);

            Map<String, Object> payload = new HashMap<>();
            payload.put("inputs", prompt);
            payload.put("parameters", params);

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);

            ResponseEntity<Object> resp = rest.postForEntity(endpoint, req, Object.class);
            org.springframework.http.HttpStatusCode status = resp.getStatusCode();
            Object body = resp.getBody();
            String bodyStr = body == null ? "" : body.toString();

            if (!status.is2xxSuccessful() || body == null) {
                log.warn("AI endpoint returned non-2xx: {} body={} for message={}", status.value(), bodyStr, userMessage);
                return Optional.of(fallback);
            }

            String generated = null;

            // Hugging Face style: List[ { "generated_text": "..." } ]
            if (body instanceof List) {
                List<?> list = (List<?>) body;
                if (!list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map) {
                        Object gen = ((Map<?,?>) first).get("generated_text");
                        if (gen != null) {
                            generated = gen.toString().trim();
                        }
                    } else if (first instanceof String) {
                        generated = first.toString();
                    }
                }
            }

            // OpenAI-compatible shapes
            if (generated == null && body instanceof Map) {
                Map<?,?> bmap = (Map<?,?>) body;
                Object reply = bmap.get("reply");
                if (reply == null) reply = bmap.get("text");
                if (reply == null) reply = bmap.get("output");
                if (reply == null) {
                    Object choices = bmap.get("choices");
                    if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                        Object c0 = ((List<?>) choices).get(0);
                        if (c0 instanceof Map) {
                            Object text = ((Map<?,?>) c0).get("text");
                            if (text != null) reply = text;
                            else {
                                Object msg = ((Map<?,?>) c0).get("message");
                                if (msg instanceof Map) {
                                    Object cont = ((Map<?,?>) msg).get("content");
                                    if (cont != null) reply = cont;
                                }
                            }
                        }
                    }
                }
                if (reply != null) generated = reply.toString().trim();
            }

            if (generated == null || generated.isBlank()) {
                log.info("AI returned empty response body, using fallback for message='{}'", userMessage);
                return Optional.of(fallback);
            }

            log.info("AI returned: {}", generated);
            return Optional.of(generated);

        } catch (HttpStatusCodeException hsce) {
            String respBody = hsce.getResponseBodyAsString();
            log.warn("AI request failed: status={} body={}", hsce.getStatusCode().value(), respBody);
            return Optional.of(fallback);
        } catch (Exception e) {
            log.error("AI request error", e);
            return Optional.of(fallback);
        }
    }

    /** Backward-compatible single-arg overload. */
    public Optional<String> sendMessage(String message) {
        return sendMessage(message, List.of());
    }

    /**
     * Simple controller-friendly chat wrapper that always returns a String
     * (never Optional.empty) and uses a dynamic fallback when necessary.
     */
    public String chat(String userMessage) {
        return sendMessage(userMessage, List.of()).orElse("You asked: \"" + (userMessage == null ? "" : userMessage) + "\". AI is currently unavailable. " +
                "Try selecting from: Starters, Main Course, Drinks, Desserts, or Other.");
    }
}
