package com.buildquote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Service
public class GooglePlacesService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesService.class);
    private static final String PLACES_API_URL = "https://places.googleapis.com/v1/places:searchText";

    @Value("${google.places.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class PlaceResult {
        public String placeId;
        public String name;
        public String formattedAddress;
        public String phone;
        public String website;
        public BigDecimal rating;
        public Integer reviewCount;
        public String city;

        @Override
        public String toString() {
            return name + " (" + formattedAddress + ")";
        }
    }

    public List<PlaceResult> searchPlaces(String query, String location) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Google Places API key not configured");
            return Collections.emptyList();
        }

        List<PlaceResult> results = new ArrayList<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Goog-Api-Key", apiKey);
            headers.set("X-Goog-FieldMask",
                "places.id,places.displayName,places.formattedAddress," +
                "places.nationalPhoneNumber,places.websiteUri,places.rating," +
                "places.userRatingCount,places.addressComponents");

            String fullQuery = query + " " + location + " Estonia";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("textQuery", fullQuery);
            requestBody.put("languageCode", "et");
            requestBody.put("regionCode", "EE");
            requestBody.put("maxResultCount", 20);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                PLACES_API_URL, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode places = root.get("places");

                if (places != null && places.isArray()) {
                    for (JsonNode place : places) {
                        PlaceResult result = new PlaceResult();
                        result.placeId = getTextValue(place, "id");

                        JsonNode displayName = place.get("displayName");
                        if (displayName != null) {
                            result.name = getTextValue(displayName, "text");
                        }

                        result.formattedAddress = getTextValue(place, "formattedAddress");
                        result.phone = getTextValue(place, "nationalPhoneNumber");
                        result.website = getTextValue(place, "websiteUri");

                        if (place.has("rating")) {
                            result.rating = BigDecimal.valueOf(place.get("rating").asDouble());
                        }
                        if (place.has("userRatingCount")) {
                            result.reviewCount = place.get("userRatingCount").asInt();
                        }

                        // Extract city from address components
                        JsonNode addressComponents = place.get("addressComponents");
                        if (addressComponents != null && addressComponents.isArray()) {
                            for (JsonNode comp : addressComponents) {
                                JsonNode types = comp.get("types");
                                if (types != null && types.isArray()) {
                                    for (JsonNode type : types) {
                                        if ("locality".equals(type.asText())) {
                                            JsonNode longText = comp.get("longText");
                                            if (longText != null) {
                                                result.city = longText.asText();
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        if (result.city == null || result.city.isEmpty()) {
                            result.city = location;
                        }

                        if (result.placeId != null && result.name != null) {
                            results.add(result);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error searching Google Places for '{}' in '{}': {}", query, location, e.getMessage());
        }

        return results;
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
