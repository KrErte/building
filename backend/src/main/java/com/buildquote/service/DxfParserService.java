package com.buildquote.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for parsing DXF (Drawing Exchange Format) CAD files.
 * DXF is a text-based format for representing AutoCAD drawings.
 */
@Service
@Slf4j
public class DxfParserService {

    /**
     * Parse DXF file and extract drawing information
     */
    public DxfParseResult parseDxf(InputStream inputStream) throws IOException {
        log.info("Starting DXF file parsing");

        Map<String, Integer> entityCounts = new HashMap<>();
        List<String> layers = new ArrayList<>();
        List<String> textContent = new ArrayList<>();
        List<Double> dimensions = new ArrayList<>();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            String previousCode = "";
            String currentEntityType = "";
            boolean inEntities = false;
            boolean inLayers = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Track section
                if (line.equals("ENTITIES")) {
                    inEntities = true;
                    inLayers = false;
                } else if (line.equals("TABLES") || line.equals("LAYER")) {
                    inLayers = true;
                } else if (line.equals("ENDSEC")) {
                    inEntities = false;
                    inLayers = false;
                }

                // Group code 0 indicates entity type
                if (previousCode.equals("0")) {
                    if (inEntities) {
                        currentEntityType = line;
                        entityCounts.merge(line, 1, Integer::sum);
                    }
                }

                // Group code 8 is layer name
                if (previousCode.equals("8") && inEntities) {
                    if (!layers.contains(line)) {
                        layers.add(line);
                    }
                }

                // Group code 1 is text content
                if (previousCode.equals("1")) {
                    if (!line.isEmpty() && !line.equals("STANDARD")) {
                        textContent.add(line);
                    }
                }

                // Extract coordinates (group codes 10, 11, 20, 21 for x,y coords)
                try {
                    if (previousCode.equals("10") || previousCode.equals("11")) {
                        double x = Double.parseDouble(line);
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                    if (previousCode.equals("20") || previousCode.equals("21")) {
                        double y = Double.parseDouble(line);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                } catch (NumberFormatException ignored) {}

                // Extract dimension values (group code 42 for actual measurement)
                if (previousCode.equals("42")) {
                    try {
                        double dim = Double.parseDouble(line);
                        if (dim > 0 && dim < 100000) {  // Reasonable dimension range
                            dimensions.add(dim);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                previousCode = line;
            }
        }

        return buildResult(entityCounts, layers, textContent, dimensions, minX, minY, maxX, maxY);
    }

    private DxfParseResult buildResult(Map<String, Integer> entityCounts,
                                        List<String> layers,
                                        List<String> textContent,
                                        List<Double> dimensions,
                                        double minX, double minY,
                                        double maxX, double maxY) {

        StringBuilder description = new StringBuilder();
        description.append("CAD jooniselt tuvastatud elemendid:\n\n");

        // Drawing entities
        description.append("JOONISE ELEMENDID:\n");
        int totalEntities = 0;
        for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
            String entityName = translateEntityName(entry.getKey());
            if (entityName != null) {
                description.append("- ").append(entityName).append(": ").append(entry.getValue()).append(" tk\n");
                totalEntities += entry.getValue();
            }
        }

        // Layers (often represent different building systems)
        if (!layers.isEmpty()) {
            description.append("\nKIHID (süsteemid):\n");
            for (String layer : layers) {
                String translated = translateLayerName(layer);
                description.append("- ").append(translated).append("\n");
            }
        }

        // Extract relevant text (dimensions, room names, etc.)
        List<String> relevantText = filterRelevantText(textContent);
        if (!relevantText.isEmpty()) {
            description.append("\nTEKSTID JA MÄRKUSED:\n");
            for (String text : relevantText.subList(0, Math.min(20, relevantText.size()))) {
                description.append("- ").append(text).append("\n");
            }
        }

        // Drawing dimensions
        if (minX != Double.MAX_VALUE && maxX != Double.MIN_VALUE) {
            double width = Math.abs(maxX - minX);
            double height = Math.abs(maxY - minY);

            // Convert to meters if values seem to be in mm
            if (width > 1000) {
                width /= 1000;
                height /= 1000;
            }

            if (width > 0 && width < 1000 && height > 0 && height < 1000) {
                description.append("\nGABARIIDID:\n");
                description.append("- Laius: ~").append(String.format("%.1f", width)).append(" m\n");
                description.append("- Kõrgus: ~").append(String.format("%.1f", height)).append(" m\n");
                description.append("- Pindala: ~").append(String.format("%.1f", width * height)).append(" m²\n");
            }
        }

        // Extract dimension measurements
        if (!dimensions.isEmpty()) {
            description.append("\nMÕÕTMED:\n");
            DoubleSummaryStatistics stats = dimensions.stream()
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
            description.append("- Mõõtmeid kokku: ").append(dimensions.size()).append(" tk\n");
            description.append("- Min: ").append(String.format("%.2f", stats.getMin())).append("\n");
            description.append("- Max: ").append(String.format("%.2f", stats.getMax())).append("\n");
        }

        description.append("\nKOKKUVÕTE:\n");
        description.append("- Elemente kokku: ").append(totalEntities).append(" tk\n");
        description.append("- Kihte: ").append(layers.size()).append(" tk\n");

        DxfParseResult result = new DxfParseResult();
        result.setDescription(description.toString());
        result.setEntityCounts(entityCounts);
        result.setLayers(layers);
        result.setTextContent(relevantText);
        result.setTotalEntities(totalEntities);

        log.info("DXF parsing complete: {} entities, {} layers", totalEntities, layers.size());

        return result;
    }

    private String translateEntityName(String entity) {
        return switch (entity.toUpperCase()) {
            case "LINE" -> "Jooned";
            case "POLYLINE", "LWPOLYLINE" -> "Polüjooned";
            case "CIRCLE" -> "Ringid";
            case "ARC" -> "Kaared";
            case "TEXT", "MTEXT" -> "Tekstid";
            case "DIMENSION" -> "Mõõtmed";
            case "HATCH" -> "Viirutused";
            case "INSERT" -> "Blokid";
            case "SOLID", "3DSOLID" -> "Pinnaelemendid";
            case "ELLIPSE" -> "Ellipsid";
            case "SPLINE" -> "Splainid";
            case "POINT" -> "Punktid";
            case "LEADER" -> "Viited";
            case "VIEWPORT" -> null;  // Skip viewports
            case "ATTRIB", "ATTDEF" -> null;  // Skip attributes
            default -> null;
        };
    }

    private String translateLayerName(String layer) {
        String upper = layer.toUpperCase();

        if (upper.contains("WALL") || upper.contains("SEIN")) return "Seinad (" + layer + ")";
        if (upper.contains("DOOR") || upper.contains("UKS")) return "Uksed (" + layer + ")";
        if (upper.contains("WINDOW") || upper.contains("AKEN")) return "Aknad (" + layer + ")";
        if (upper.contains("ELEC") || upper.contains("ELEKTR")) return "Elektrisüsteem (" + layer + ")";
        if (upper.contains("PLUMB") || upper.contains("SANIT") || upper.contains("TORU")) return "Torustik (" + layer + ")";
        if (upper.contains("HVAC") || upper.contains("VENT")) return "Ventilatsioon (" + layer + ")";
        if (upper.contains("FLOOR") || upper.contains("PÕRAND")) return "Põrandad (" + layer + ")";
        if (upper.contains("ROOF") || upper.contains("KATUS")) return "Katus (" + layer + ")";
        if (upper.contains("STAIR") || upper.contains("TREPP")) return "Trepid (" + layer + ")";
        if (upper.contains("FURN") || upper.contains("MÖÖB")) return "Mööbel (" + layer + ")";
        if (upper.contains("DIM") || upper.contains("MÕÕT")) return "Mõõtmed (" + layer + ")";
        if (upper.contains("TEXT") || upper.contains("TEKST")) return "Tekstid (" + layer + ")";
        if (upper.contains("HATCH")) return "Viirutused (" + layer + ")";

        return layer;
    }

    private List<String> filterRelevantText(List<String> textContent) {
        List<String> relevant = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String text : textContent) {
            // Skip empty, too short, or duplicate
            if (text == null || text.length() < 2 || seen.contains(text.toLowerCase())) {
                continue;
            }

            // Skip purely numeric without units
            if (text.matches("^[\\d.]+$")) {
                continue;
            }

            // Skip common CAD noise
            if (text.matches("(?i)^(standard|bylayer|byblock|continuous|0)$")) {
                continue;
            }

            seen.add(text.toLowerCase());
            relevant.add(text);
        }

        return relevant;
    }

    /**
     * Result of DXF parsing
     */
    public static class DxfParseResult {
        private String description;
        private Map<String, Integer> entityCounts;
        private List<String> layers;
        private List<String> textContent;
        private int totalEntities;

        // Getters and setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Integer> getEntityCounts() { return entityCounts; }
        public void setEntityCounts(Map<String, Integer> entityCounts) { this.entityCounts = entityCounts; }
        public List<String> getLayers() { return layers; }
        public void setLayers(List<String> layers) { this.layers = layers; }
        public List<String> getTextContent() { return textContent; }
        public void setTextContent(List<String> textContent) { this.textContent = textContent; }
        public int getTotalEntities() { return totalEntities; }
        public void setTotalEntities(int totalEntities) { this.totalEntities = totalEntities; }
    }
}
