package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC Material usage information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcMaterialInfo(
    String name,
    int usageCount
) {}
