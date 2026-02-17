package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Root DTO for IFC parsing results from IfcOpenShell.
 * Contains all extracted building information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcBuildingData(
    IfcFileInfo fileInfo,
    IfcProjectInfo project,
    IfcSpatialStructure spatialStructure,
    List<IfcSpaceInfo> spaces,
    List<IfcElementInfo> structuralElements,
    List<IfcElementInfo> openings,
    List<IfcMepElementInfo> mepElements,
    List<IfcMaterialInfo> materials,
    IfcQuantitySummary quantitySummary,
    int parseTimeMs
) {}
