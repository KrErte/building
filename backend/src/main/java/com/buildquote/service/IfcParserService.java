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
 * Service for parsing IFC (Industry Foundation Classes) BIM files.
 * IFC files are text-based STEP format containing building model data.
 */
@Service
@Slf4j
public class IfcParserService {

    // Patterns for extracting IFC entities
    private static final Pattern ENTITY_PATTERN = Pattern.compile("#(\\d+)\\s*=\\s*(IFC\\w+)\\s*\\((.*)\\);");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("IFCQUANTITY(\\w+)\\s*\\([^)]*'([^']*)'[^)]*,([\\d.]+)");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("IFCPROPERTY\\w+\\s*\\([^)]*'([^']*)'[^)]*,([^)]+)\\)");

    /**
     * Parse IFC file and extract building elements with quantities
     */
    public IfcParseResult parseIfc(InputStream inputStream) throws IOException {
        log.info("Starting IFC file parsing");

        Map<String, Integer> elementCounts = new HashMap<>();
        Map<String, List<Double>> elementAreas = new HashMap<>();
        Map<String, List<Double>> elementVolumes = new HashMap<>();
        Map<String, Set<String>> elementMaterials = new HashMap<>();
        List<String> spaces = new ArrayList<>();
        List<String> materials = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            StringBuilder currentEntity = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("/*") || line.startsWith("*")) {
                    continue;
                }

                currentEntity.append(line);

                // Process complete entity (ends with semicolon)
                if (line.endsWith(";")) {
                    processEntity(currentEntity.toString(), elementCounts, elementAreas,
                                  elementVolumes, elementMaterials, spaces, materials);
                    currentEntity = new StringBuilder();
                }
            }
        }

        return buildResult(elementCounts, elementAreas, elementVolumes, elementMaterials, spaces, materials);
    }

    private void processEntity(String entity, Map<String, Integer> counts,
                               Map<String, List<Double>> areas, Map<String, List<Double>> volumes,
                               Map<String, Set<String>> materials, List<String> spaces,
                               List<String> materialsList) {

        String entityUpper = entity.toUpperCase();

        // Count building elements
        if (entityUpper.contains("IFCWALL")) {
            counts.merge("Seinad", 1, Integer::sum);
            extractQuantities(entity, "Seinad", areas, volumes);
        }
        if (entityUpper.contains("IFCSLAB")) {
            counts.merge("Põrandad/Laed", 1, Integer::sum);
            extractQuantities(entity, "Põrandad/Laed", areas, volumes);
        }
        if (entityUpper.contains("IFCDOOR")) {
            counts.merge("Uksed", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCWINDOW")) {
            counts.merge("Aknad", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCROOF")) {
            counts.merge("Katus", 1, Integer::sum);
            extractQuantities(entity, "Katus", areas, volumes);
        }
        if (entityUpper.contains("IFCSTAIR")) {
            counts.merge("Trepid", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCRAILING")) {
            counts.merge("Piirded", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCCOLUMN")) {
            counts.merge("Sambad", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCBEAM")) {
            counts.merge("Talad", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCCOVERING")) {
            counts.merge("Katted", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCCURTAINWALL")) {
            counts.merge("Klaasseinad", 1, Integer::sum);
        }

        // MEP Systems
        if (entityUpper.contains("IFCPIPE") || entityUpper.contains("IFCFLOWSEGMENT")) {
            counts.merge("Torustik", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCDUCTSEQ") || entityUpper.contains("IFCAIRTERM")) {
            counts.merge("Ventilatsioon", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCCABLESEGMENT") || entityUpper.contains("IFCELECTRICALDIST")) {
            counts.merge("Elektrisüsteem", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCSANITARYTERM")) {
            counts.merge("Sanitaartehnika", 1, Integer::sum);
        }
        if (entityUpper.contains("IFCLIGHTFIXTURE")) {
            counts.merge("Valgustid", 1, Integer::sum);
        }

        // Extract spaces
        if (entityUpper.contains("IFCSPACE")) {
            Pattern spaceNamePattern = Pattern.compile("'([^']+)'");
            Matcher matcher = spaceNamePattern.matcher(entity);
            if (matcher.find()) {
                String spaceName = matcher.group(1);
                if (!spaceName.isEmpty() && !spaceName.equals("$")) {
                    spaces.add(spaceName);
                }
            }
        }

        // Extract materials
        if (entityUpper.contains("IFCMATERIAL") && !entityUpper.contains("IFCMATERIALLIST")) {
            Pattern materialPattern = Pattern.compile("IFCMATERIAL\\s*\\(\\s*'([^']+)'");
            Matcher matcher = materialPattern.matcher(entity);
            if (matcher.find()) {
                String materialName = matcher.group(1);
                if (!materialName.isEmpty() && !materialsList.contains(materialName)) {
                    materialsList.add(materialName);
                }
            }
        }
    }

    private void extractQuantities(String entity, String elementType,
                                   Map<String, List<Double>> areas,
                                   Map<String, List<Double>> volumes) {
        // Try to extract area
        Pattern areaPattern = Pattern.compile("(\\d+\\.?\\d*)\\s*(?:m2|M2|m²)");
        Matcher areaMatcher = areaPattern.matcher(entity);
        if (areaMatcher.find()) {
            double area = Double.parseDouble(areaMatcher.group(1));
            areas.computeIfAbsent(elementType, k -> new ArrayList<>()).add(area);
        }

        // Try to extract volume
        Pattern volumePattern = Pattern.compile("(\\d+\\.?\\d*)\\s*(?:m3|M3|m³)");
        Matcher volumeMatcher = volumePattern.matcher(entity);
        if (volumeMatcher.find()) {
            double volume = Double.parseDouble(volumeMatcher.group(1));
            volumes.computeIfAbsent(elementType, k -> new ArrayList<>()).add(volume);
        }
    }

    private IfcParseResult buildResult(Map<String, Integer> counts,
                                        Map<String, List<Double>> areas,
                                        Map<String, List<Double>> volumes,
                                        Map<String, Set<String>> materials,
                                        List<String> spaces,
                                        List<String> materialsList) {

        StringBuilder description = new StringBuilder();
        description.append("BIM mudelist tuvastatud ehituselemendid:\n\n");

        // Building elements
        description.append("EHITUSELEMENDID:\n");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            description.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" tk");

            // Add area if available
            List<Double> areaList = areas.get(entry.getKey());
            if (areaList != null && !areaList.isEmpty()) {
                double totalArea = areaList.stream().mapToDouble(Double::doubleValue).sum();
                description.append(", kokku ~").append(String.format("%.1f", totalArea)).append(" m²");
            }

            description.append("\n");
        }

        // Spaces
        if (!spaces.isEmpty()) {
            description.append("\nRUUMID:\n");
            Set<String> uniqueSpaces = new LinkedHashSet<>(spaces);
            for (String space : uniqueSpaces) {
                description.append("- ").append(space).append("\n");
            }
        }

        // Materials
        if (!materialsList.isEmpty()) {
            description.append("\nMATERJALID:\n");
            for (String material : materialsList) {
                description.append("- ").append(material).append("\n");
            }
        }

        // Calculate totals
        int totalElements = counts.values().stream().mapToInt(Integer::intValue).sum();
        double totalArea = areas.values().stream()
                .flatMap(List::stream)
                .mapToDouble(Double::doubleValue)
                .sum();

        description.append("\nKOKKUVÕTE:\n");
        description.append("- Ehituselemente kokku: ").append(totalElements).append(" tk\n");
        if (totalArea > 0) {
            description.append("- Pindalasid kokku: ~").append(String.format("%.1f", totalArea)).append(" m²\n");
        }
        description.append("- Ruume: ").append(spaces.size()).append(" tk\n");
        description.append("- Materjale: ").append(materialsList.size()).append(" tk\n");

        IfcParseResult result = new IfcParseResult();
        result.setDescription(description.toString());
        result.setElementCounts(counts);
        result.setTotalElements(totalElements);
        result.setTotalArea(totalArea);
        result.setSpaces(spaces);
        result.setMaterials(materialsList);

        log.info("IFC parsing complete: {} elements, {} spaces, {} materials",
                totalElements, spaces.size(), materialsList.size());

        return result;
    }

    /**
     * Result of IFC parsing
     */
    public static class IfcParseResult {
        private String description;
        private Map<String, Integer> elementCounts;
        private int totalElements;
        private double totalArea;
        private List<String> spaces;
        private List<String> materials;

        // Getters and setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Integer> getElementCounts() { return elementCounts; }
        public void setElementCounts(Map<String, Integer> elementCounts) { this.elementCounts = elementCounts; }
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
        public double getTotalArea() { return totalArea; }
        public void setTotalArea(double totalArea) { this.totalArea = totalArea; }
        public List<String> getSpaces() { return spaces; }
        public void setSpaces(List<String> spaces) { this.spaces = spaces; }
        public List<String> getMaterials() { return materials; }
        public void setMaterials(List<String> materials) { this.materials = materials; }
    }
}
