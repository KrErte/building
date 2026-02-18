package com.buildquote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuoteParserService {

    private final AnthropicService anthropicService;
    private final AiCacheService aiCacheService;
    private final ObjectMapper objectMapper;

    private static final String QUOTE_PARSE_PROMPT = """
        You are a construction quote parser. Extract structured data from this email/message
        that contains a supplier's bid/quote response.

        Extract the following:
        - totalPrice: the total quoted price as a number
        - currency: the currency (EUR, USD, etc.)
        - lineItems: array of {description, quantity, unit, unitPrice, totalPrice}
        - timelineDays: estimated delivery/completion in days
        - validUntil: quote validity date if mentioned
        - conditions: any terms, conditions, or caveats
        - sentiment: POSITIVE, NEUTRAL, or NEGATIVE (supplier's tone/willingness)

        Return ONLY valid JSON:
        {
          "totalPrice": number,
          "currency": "string",
          "lineItems": [...],
          "timelineDays": number or null,
          "validUntil": "string or null",
          "conditions": ["string"],
          "sentiment": "string"
        }

        Email/message text:
        """;

    public Map<String, Object> parseQuoteFromEmail(String emailText) {
        String cacheKey = "quote_parse:" + emailText.hashCode();
        Optional<String> cached = aiCacheService.getCached(cacheKey, "quote-parser");

        String response;
        if (cached.isPresent()) {
            response = cached.get();
        } else {
            response = anthropicService.callClaude(QUOTE_PARSE_PROMPT + emailText);
            if (response != null) {
                aiCacheService.cache(cacheKey, "quote-parser", response);
            }
        }

        if (response == null) {
            log.warn("Failed to parse quote from email");
            return Map.of("error", "Failed to parse quote");
        }

        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> result = new HashMap<>();

            result.put("totalPrice", root.has("totalPrice") ? new BigDecimal(root.get("totalPrice").asText("0")) : null);
            result.put("currency", root.path("currency").asText("EUR"));
            result.put("timelineDays", root.has("timelineDays") && !root.get("timelineDays").isNull()
                    ? root.get("timelineDays").asInt() : null);
            result.put("validUntil", root.path("validUntil").asText(null));
            result.put("sentiment", root.path("sentiment").asText("NEUTRAL"));

            List<String> conditions = new ArrayList<>();
            if (root.has("conditions") && root.get("conditions").isArray()) {
                root.get("conditions").forEach(c -> conditions.add(c.asText()));
            }
            result.put("conditions", conditions);

            List<Map<String, Object>> lineItems = new ArrayList<>();
            if (root.has("lineItems") && root.get("lineItems").isArray()) {
                for (JsonNode item : root.get("lineItems")) {
                    Map<String, Object> li = new HashMap<>();
                    li.put("description", item.path("description").asText());
                    li.put("quantity", item.path("quantity").asDouble());
                    li.put("unit", item.path("unit").asText());
                    li.put("unitPrice", item.path("unitPrice").asDouble());
                    li.put("totalPrice", item.path("totalPrice").asDouble());
                    lineItems.add(li);
                }
            }
            result.put("lineItems", lineItems);

            return result;
        } catch (Exception e) {
            log.error("Error parsing quote response: {}", e.getMessage());
            return Map.of("error", "Parse error: " + e.getMessage());
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
