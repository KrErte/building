package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * IFC MEP (Mechanical, Electrical, Plumbing) element information.
 * Includes pipes, ducts, terminals, fittings, equipment, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcMepElementInfo(
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
    String systemType,
    String predefinedType,
    Double nominalDiameter,
    Double length,
    Double innerDiameter,
    Double outerDiameter,
    Double flowRate,
    Double pressure,
    Map<String, Object> properties,
    Map<String, Double> quantities
) {}
