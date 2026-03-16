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
     * History lines are prepended to the prompt so the model has context and
     * doesn't return the same response for every call.
     *
     * @param message the current user message
     * @param history prior turns, each formatted as "User: ..." or "Assistant: ..."
     * @return the AI response, or empty if endpoint/key not configured
     */
    public Optional<String> sendMessage(String message, List<String> history) {
        if (endpoint.isBlank() || apiKey.isBlank()) return Optional.empty();

        // Build context-enriched prompt
        String prompt = buildPrompt(message, history);
        log.debug("sendMessage: prompt length={} endpoint={}", prompt.length(), endpoint);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Hugging Face style: POST { "inputs": "..." }
            Map<String, Object> hfPayload = Map.of("inputs", prompt);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(hfPayload, headers);

            ResponseEntity<Object> resp = rest.postForEntity(endpoint, req, Object.class);
            org.springframework.http.HttpStatusCode status = resp.getStatusCode();
            Object body = resp.getBody();
            String bodyStr = body == null ? "" : body.toString();

            if (!status.is2xxSuccessful() || body == null) {
                log.warn("AI endpoint returned non-2xx: {} body={} for message={}", status.value(), bodyStr, message);
                return Optional.of("Assistant (error): HTTP " + status.value() + " - " + bodyStr);
            }

            // Hugging Face returns List of objects with 'generated_text'
            if (body instanceof List) {
                List<?> list = (List<?>) body;
                if (!list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map) {
                        Object gen = ((Map<?,?>) first).get("generated_text");
                        if (gen != null) {
                            // Strip the prompt from generated_text (HF models echo the input)
                            String generated = gen.toString();
                            if (generated.startsWith(prompt)) {
                                generated = generated.substring(prompt.length()).trim();
                            }
                            if (!generated.isBlank()) return Optional.of(generated);
                        }
                    } else if (first instanceof String) {
                        return Optional.of(first.toString());
                    }
                }
            }

            // OpenAI-compatible response shape
            if (body instanceof Map) {
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
                if (reply != null) return Optional.of(reply.toString());
            }

            // Last resort: convert body to string
            return Optional.of(body.toString());

        } catch (HttpStatusCodeException hsce) {
            String respBody = hsce.getResponseBodyAsString();
            log.warn("AI request failed: status={} body={}", hsce.getStatusCode().value(), respBody);
            return Optional.of("Assistant (error): HTTP " + hsce.getStatusCode().value() + " - " + respBody);
        } catch (Exception e) {
            log.error("AI request error", e);
            return Optional.of("Assistant (error): " + e.getMessage());
        }
    }

    /** Backward-compatible single-arg overload. */
    public Optional<String> sendMessage(String message) {
        return sendMessage(message, List.of());
    }

    /**
     * Builds a prompt string by prepending conversation history so the model
     * receives multi-turn context. GPT-2 / HF text-gen models work best with
     * a simple "User/Assistant:" dialogue format.
     */
    private String buildPrompt(String message, List<String> history) {
        if (history == null || history.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder();
        for (String turn : history) {
            sb.append(turn).append("\n");
        }
        sb.append("User: ").append(message).append("\nAssistant:");
        return sb.toString();
    }
}
