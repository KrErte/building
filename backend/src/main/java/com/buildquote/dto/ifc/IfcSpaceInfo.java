package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC Space (room) information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcSpaceInfo(
    String guid,
    String name,
    String longName,
    String storeyName,
    double area,
    double volume,
    double height
) {}
