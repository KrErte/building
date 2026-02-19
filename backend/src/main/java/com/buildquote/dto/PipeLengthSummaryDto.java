package com.buildquote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PipeLengthSummaryDto {
    private String color;       // PURPLE, BROWN, BLUE
    private String colorLabel;  // Lilla torud, Pruunid torud, Sinised torud
    private String description; // What this color represents
    private BigDecimal totalLengthM = BigDecimal.ZERO;
    private List<PipeItem> pipes = new ArrayList<>();

    @Data
    public static class PipeItem {
        private String name;           // e.g. "PPR toru Ø20"
        private BigDecimal lengthM;
        private String diameter;       // e.g. "Ø20"
        private List<String> sourceStages = new ArrayList<>();
    }
}
