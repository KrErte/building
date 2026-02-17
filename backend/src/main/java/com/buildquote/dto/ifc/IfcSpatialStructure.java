package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * IFC spatial structure: sites, buildings, storeys.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcSpatialStructure(
    List<IfcSiteInfo> sites,
    List<IfcBuildingInfo> buildings,
    List<IfcStoreyInfo> storeys
) {}
