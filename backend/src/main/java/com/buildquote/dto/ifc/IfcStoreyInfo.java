package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC Building Storey information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcStoreyInfo(
    String guid,
    String name,
    double elevation
) {}
