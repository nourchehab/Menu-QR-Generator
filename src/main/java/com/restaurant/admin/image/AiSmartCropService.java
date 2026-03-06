package com.restaurant.admin.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * Uses the Anthropic Claude Vision API to detect the primary food subject in a
 * menu image and return a {@link CropWindow} describing the region of interest.
 *
 * <p>
 * If the API key is not configured, or if the model response cannot be parsed,
 * the service returns {@link CropWindow#FULL} so the pipeline falls back to a
 * standard centre-crop.</p>
 *
 * <h3>Prompt design</h3>
 * We ask the model for a JSON object with fractional bounding-box coordinates
 * and a confidence score. Using structured output (JSON-only prompt) avoids the
 * need for a full tool-use integration.
 */
@Service
public class AiSmartCropService {

    private static final Logger log = LoggerFactory.getLogger(AiSmartCropService.class);

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL = "claude-opus-4-6";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * Set via {@code ANTHROPIC_API_KEY} environment variable or
     * {@code anthropic.api-key} in application.properties. When blank,
     * smart-crop is silently disabled.
     */
    @Value("${anthropic.api-key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Analyse the supplied JPEG/PNG bytes and return the best crop window for
     * the primary food subject.
     *
     * @param imageBytes raw bytes of the original (pre-processed) image
     * @param mimeType e.g. "image/jpeg" or "image/png"
     * @return a {@link CropWindow} or {@link CropWindow#FULL} on any failure
     */
    public CropWindow detectSubjectCrop(byte[] imageBytes, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("AI smart-crop disabled (no API key configured).");
            return CropWindow.FULL;
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            String requestBody = buildRequestBody(base64Image, mimeType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response
                    = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Claude API returned HTTP {}: {}", response.statusCode(), response.body());
                return CropWindow.FULL;
            }

            return parseCropWindow(response.body());

        } catch (Exception e) {
            log.warn("AI smart-crop failed, using centre-crop fallback: {}", e.getMessage());
            return CropWindow.FULL;
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────
    private String buildRequestBody(String base64Image, String mimeType) {
        // language=json
        return """
                {
                  "model": "%s",
                  "max_tokens": 256,
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {
                          "type": "image",
                          "source": {
                            "type": "base64",
                            "media_type": "%s",
                            "data": "%s"
                          }
                        },
                        {
                          "type": "text",
                          "text": "You are a food photography AI. Identify the primary food subject in this menu item image. Return ONLY a JSON object (no markdown, no extra text) with these fields:\\n{\\n  \\"x\\": <fraction 0-1, left edge of subject>,\\n  \\"y\\": <fraction 0-1, top edge of subject>,\\n  \\"width\\": <fraction 0-1, width of subject bounding box>,\\n  \\"height\\": <fraction 0-1, height of subject bounding box>,\\n  \\"confidence\\": <0-1, how confident you are>\\n}\\nCenter the bounding box tightly around the main food item, leaving ~10%% margin. If there is no clear food subject, return x=0, y=0, width=1, height=1, confidence=0."
                        }
                      ]
                    }
                  ]
                }
                """.formatted(CLAUDE_MODEL, mimeType, base64Image);
    }

    private CropWindow parseCropWindow(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        // Claude returns: { content: [ { type: "text", text: "..." } ] }
        String text = root.path("content").get(0).path("text").asText();

        // Strip any accidental markdown fences
        String json = text.replaceAll("```[a-z]*", "").replace("```", "").trim();

        JsonNode crop = mapper.readTree(json);
        double x = crop.path("x").asDouble(0);
        double y = crop.path("y").asDouble(0);
        double w = crop.path("width").asDouble(1);
        double h = crop.path("height").asDouble(1);
        double conf = crop.path("confidence").asDouble(0);

        CropWindow window = new CropWindow(x, y, w, h, conf);
        log.debug("AI crop window: x={} y={} w={} h={} confidence={}", x, y, w, h, conf);
        return window;
    }
}
