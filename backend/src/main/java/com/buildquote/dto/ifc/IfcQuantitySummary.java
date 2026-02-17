package com.buildquote.dto.ifc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Summary of quantities extracted from IFC file.
 * Used for quick overview and quote generation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfcQuantitySummary(
    // Total counts
    int totalElements,
    int totalMepElements,
    // Structural counts
    int wallCount,
    int slabCount,
    int columnCount,
    int beamCount,
    int doorCount,
    int windowCount,
    // MEP counts
    int pipeSegmentCount,
    int pipeFittingCount,
    int ductSegmentCount,
    int ductFittingCount,
    int flowTerminalCount,
    int valveCount,
    int pumpCount,
    int boilerCount,
    int fanCount,
    int filterCount,
    // Aggregated measurements
    double totalPipeLength,
    double totalDuctLength,
    double totalWallArea,
    double totalSlabArea,
    // Breakdowns
    Map<String, Integer> elementCountBySystem,
    Map<String, Double> pipeLengthBySystem,
    Map<String, Double> ductLengthBySystem,
    Map<String, Integer> elementCountByMaterial
) {}
