package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IFC file header metadata.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcFileInfo(
    String schemaVersion,
    String fileName,
    String timestamp,
    String author,
    String organization,
    String originatingSystem,
    String preprocessor,
    String description
) {}
