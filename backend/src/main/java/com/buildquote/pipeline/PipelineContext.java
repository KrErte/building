package com.buildquote.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UUID pipelineId;
    private UUID projectId;
    private UUID userId;
    private Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        // Try JSON conversion for complex types
        try {
            return MAPPER.convertValue(value, type);
        } catch (Exception e) {
            log.warn("Failed to convert context value for key '{}' to {}: {}", key, type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize PipelineContext: {}", e.getMessage());
            return "{}";
        }
    }

    public static PipelineContext fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return new PipelineContext();
            return MAPPER.readValue(json, PipelineContext.class);
        } catch (Exception e) {
            log.error("Failed to deserialize PipelineContext: {}", e.getMessage());
            return new PipelineContext();
        }
    }
}
