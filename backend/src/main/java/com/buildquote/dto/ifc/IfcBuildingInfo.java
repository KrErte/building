package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC Building information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcBuildingInfo(
    String guid,
    String name,
    String description,
    String address
) {}
