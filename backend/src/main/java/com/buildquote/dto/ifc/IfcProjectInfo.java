package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC Project entity information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcProjectInfo(
    String guid,
    String name,
    String description,
    String phase
) {}
