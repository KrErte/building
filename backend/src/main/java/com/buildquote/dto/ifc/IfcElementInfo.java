package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * IFC structural element or opening information.
 * Used for walls, slabs, columns, beams, doors, windows, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcElementInfo(
    int entityId,
    String guid,
    String ifcType,
    String name,
    String description,
    String objectType,
    String tag,
    String typeName,
    String storeyName,
    String materialName,
    Map<String, Object> properties,
    Map<String, Double> quantities,
    // Openings-specific (nullable)
    @JsonProperty("overallWidth") Double overallWidth,
    @JsonProperty("overallHeight") Double overallHeight
) {}
