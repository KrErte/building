package com.buildquote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AnthropicService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callClaude(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode content = root.path("content");
                if (content.isArray() && content.size() > 0) {
                    return content.get(0).path("text").asText();
                }
            }

            log.error("Unexpected response from Anthropic API: {}", response.getBody());
            return null;
        } catch (Exception e) {
            log.error("Error calling Anthropic API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Call Claude Vision API with an image for construction plan analysis
     */
    public String callClaudeVision(byte[] imageData, String mediaType, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            String base64Image = Base64.getEncoder().encodeToString(imageData);

            // Build content array with image and text
            List<Map<String, Object>> contentParts = new ArrayList<>();

            // Add image
            contentParts.add(Map.of(
                "type", "image",
                "source", Map.of(
                    "type", "base64",
                    "media_type", mediaType,
                    "data", base64Image
                )
            ));

            // Add text prompt
            contentParts.add(Map.of(
                "type", "text",
                "text", prompt
            ));

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(
                    Map.of("role", "user", "content", contentParts)
                )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling Claude Vision API with image of size {} bytes", imageData.length);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode content = root.path("content");
                if (content.isArray() && content.size() > 0) {
                    return content.get(0).path("text").asText();
                }
            }

            log.error("Unexpected response from Anthropic Vision API: {}", response.getBody());
            return null;
        } catch (Exception e) {
            log.error("Error calling Anthropic Vision API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Call Claude Vision API with multiple images
     */
    public String callClaudeVisionMultiple(List<byte[]> images, List<String> mediaTypes, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            List<Map<String, Object>> contentParts = new ArrayList<>();

            // Add all images
            for (int i = 0; i < images.size(); i++) {
                String base64Image = Base64.getEncoder().encodeToString(images.get(i));
                contentParts.add(Map.of(
                    "type", "image",
                    "source", Map.of(
                        "type", "base64",
                        "media_type", mediaTypes.get(i),
                        "data", base64Image
                    )
                ));
            }

            // Add text prompt
            contentParts.add(Map.of(
                "type", "text",
                "text", prompt
            ));

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(
                    Map.of("role", "user", "content", contentParts)
                )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling Claude Vision API with {} images", images.size());

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode content = root.path("content");
                if (content.isArray() && content.size() > 0) {
                    return content.get(0).path("text").asText();
                }
            }

            log.error("Unexpected response from Anthropic Vision API: {}", response.getBody());
            return null;
        } catch (Exception e) {
            log.error("Error calling Anthropic Vision API: {}", e.getMessage(), e);
            return null;
        }
    }
}
