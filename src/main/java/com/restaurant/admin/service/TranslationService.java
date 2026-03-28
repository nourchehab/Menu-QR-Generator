package com.restaurant.admin.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TranslationService {

    @Value("${translation.api.url}")
    private String translateUrl;

    public String translate(String text, String sourceLang, String targetLang) {
        // Create a RestTemplate to send requests
        RestTemplate restTemplate = new RestTemplate();

        // Prepare the request parameters
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("q", text);  // Text to be translated
        map.add("source", sourceLang);  // Source language
        map.add("target", targetLang);  // Target language
        map.add("format", "text");  // Format of text to translate

        // Send POST request to LibreTranslate API and get the response
        ResponseEntity<String> response = restTemplate.postForEntity(translateUrl, map, String.class);

        // Return the translated text from the response body
        return response.getBody();
    }
}