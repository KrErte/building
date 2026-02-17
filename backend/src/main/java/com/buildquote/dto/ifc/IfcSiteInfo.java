package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC Site information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcSiteInfo(
    String guid,
    String name,
    String description
) {}
