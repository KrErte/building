package com.buildquote.service;

import com.buildquote.entity.MaterialUnitPrice;
import com.buildquote.entity.WorkMaterialBundle;
import com.buildquote.entity.WorkUnitPrice;
import com.buildquote.repository.MaterialUnitPriceRepository;
import com.buildquote.repository.WorkMaterialBundleRepository;
import com.buildquote.repository.WorkUnitPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EstimatePriceService {

    private final WorkUnitPriceRepository workUnitPriceRepository;
    private final MaterialUnitPriceRepository materialUnitPriceRepository;
    private final WorkMaterialBundleRepository workMaterialBundleRepository;

    // Mapping from IFC element types and Estonian names to work categories
    private static final Map<String, String> IFC_TO_CATEGORY = Map.ofEntries(
        // IFC element types
        Map.entry("IfcWall", "Müüritööd"),
        Map.entry("IfcWallStandardCase", "Müüritööd"),
        Map.entry("IfcSlab", "Vundament"),
        Map.entry("IfcRoof", "Katus"),
        Map.entry("IfcCovering", "Viimistlus"),
        Map.entry("IfcDoor", "Siseehitus"),
        Map.entry("IfcWindow", "Siseehitus"),
        Map.entry("IfcStair", "Siseehitus"),
        Map.entry("IfcColumn", "Vundament"),
        Map.entry("IfcBeam", "Vundament"),
        Map.entry("IfcFooting", "Vundament"),
        Map.entry("IfcPile", "Vundament"),
        Map.entry("IfcRailing", "Terrass"),
        Map.entry("IfcCurtainWall", "Fassaad"),
        Map.entry("IfcPlate", "Siseehitus"),
        Map.entry("IfcMember", "Siseehitus"),
        Map.entry("IfcBuildingElementProxy", "Siseehitus"),
        Map.entry("IfcFlowTerminal", "Torutööd"),
        Map.entry("IfcSanitaryTerminal", "Torutööd"),
        Map.entry("IfcPipeFitting", "Torutööd"),
        Map.entry("IfcPipeSegment", "Torutööd"),
        Map.entry("IfcSpaceHeater", "Torutööd"),
        Map.entry("IfcElectricAppliance", "Elekter"),
        Map.entry("IfcLightFixture", "Elekter"),
        Map.entry("IfcOutlet", "Elekter"),
        Map.entry("IfcSwitchingDevice", "Elekter"),
        Map.entry("IfcCableCarrierSegment", "Elekter"),
        Map.entry("IfcCableSegment", "Elekter"),
        // Estonian element names from basic IFC parser
        Map.entry("Seinad", "Müüritööd"),
        Map.entry("Põrandad/Laed", "Vundament"),
        Map.entry("Uksed", "Siseehitus"),
        Map.entry("Aknad", "Siseehitus"),
        Map.entry("Katus", "Katus"),
        Map.entry("Trepid", "Siseehitus"),
        Map.entry("Piirded", "Terrass"),
        Map.entry("Sambad", "Vundament"),
        Map.entry("Talad", "Vundament"),
        Map.entry("Katted", "Viimistlus"),
        Map.entry("Klaasseinad", "Fassaad"),
        Map.entry("Torustik", "Torutööd"),
        Map.entry("Ventilatsioon", "Torutööd"),
        Map.entry("Elektrisüsteem", "Elekter"),
        Map.entry("Sanitaartehnika", "Torutööd"),
        Map.entry("Valgustid", "Elekter")
    );

    public EstimateResult calculateEstimate(String description) {
        List<CategoryEstimate> categoryEstimates = new ArrayList<>();
        BigDecimal totalMin = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;

        // Parse quantities from description
        Map<String, QuantityInfo> quantities = parseQuantities(description);

        // Get all prices and bundles
        List<WorkUnitPrice> workPrices = workUnitPriceRepository.findAll();
        List<MaterialUnitPrice> materialPrices = materialUnitPriceRepository.findAll();
        List<WorkMaterialBundle> bundles = workMaterialBundleRepository.findAll();

        // Index prices by category and name
        Map<String, List<WorkUnitPrice>> workByCategory = workPrices.stream()
            .collect(Collectors.groupingBy(WorkUnitPrice::getCategory));

        Map<String, MaterialUnitPrice> materialByName = materialPrices.stream()
            .collect(Collectors.toMap(
                MaterialUnitPrice::getMaterialName,
                m -> m,
                (a, b) -> a // Keep first on duplicate
            ));

        Map<String, List<WorkMaterialBundle>> bundlesByCategory = bundles.stream()
            .collect(Collectors.groupingBy(WorkMaterialBundle::getWorkCategory));

        // Calculate estimates for each detected category
        for (Map.Entry<String, QuantityInfo> entry : quantities.entrySet()) {
            String category = entry.getKey();
            QuantityInfo qty = entry.getValue();

            BigDecimal workMin = BigDecimal.ZERO;
            BigDecimal workMax = BigDecimal.ZERO;
            BigDecimal materialMin = BigDecimal.ZERO;
            BigDecimal materialMax = BigDecimal.ZERO;

            // 1. Calculate work cost
            List<WorkUnitPrice> categoryWork = workByCategory.get(category);
            if (categoryWork != null && !categoryWork.isEmpty()) {
                // Get average of all work prices in category
                BigDecimal avgMin = BigDecimal.ZERO;
                BigDecimal avgMax = BigDecimal.ZERO;
                int count = 0;
                for (WorkUnitPrice wp : categoryWork) {
                    if (wp.getUnit().equalsIgnoreCase("m2") || wp.getUnit().equalsIgnoreCase("jm")) {
                        avgMin = avgMin.add(wp.getMinPriceEur());
                        avgMax = avgMax.add(wp.getMaxPriceEur());
                        count++;
                    }
                }
                if (count > 0) {
                    avgMin = avgMin.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                    avgMax = avgMax.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                } else {
                    // Use first available
                    avgMin = categoryWork.get(0).getMinPriceEur();
                    avgMax = categoryWork.get(0).getMaxPriceEur();
                }

                BigDecimal workQuantity = getQuantityForWork(qty);
                workMin = avgMin.multiply(workQuantity);
                workMax = avgMax.multiply(workQuantity);
            }

            // 2. Calculate material cost using bundles
            List<WorkMaterialBundle> categoryBundles = bundlesByCategory.get(category);
            if (categoryBundles != null) {
                for (WorkMaterialBundle bundle : categoryBundles) {
                    if (bundle.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
                        continue; // Skip bundles with 0 ratio
                    }

                    MaterialUnitPrice material = materialByName.get(bundle.getMaterialName());
                    if (material == null) {
                        log.debug("Material not found for bundle: {}", bundle.getMaterialName());
                        continue;
                    }

                    // Calculate material quantity based on unit type and ratio
                    BigDecimal materialQty = calculateMaterialQuantity(qty, bundle);

                    if (materialQty.compareTo(BigDecimal.ZERO) > 0) {
                        materialMin = materialMin.add(material.getMinPriceEur().multiply(materialQty));
                        materialMax = materialMax.add(material.getMaxPriceEur().multiply(materialQty));

                        log.debug("Material {}: qty={}, min={}, max={}",
                            bundle.getMaterialName(), materialQty,
                            material.getMinPriceEur().multiply(materialQty),
                            material.getMaxPriceEur().multiply(materialQty));
                    }
                }
            }

            // Total for category = work + materials
            BigDecimal categoryMin = workMin.add(materialMin);
            BigDecimal categoryMax = workMax.add(materialMax);

            if (categoryMin.compareTo(BigDecimal.ZERO) > 0 || categoryMax.compareTo(BigDecimal.ZERO) > 0) {
                String details = String.format("%s (töö: €%.0f-€%.0f, materjal: €%.0f-€%.0f)",
                    qty.description,
                    workMin, workMax,
                    materialMin, materialMax);

                categoryEstimates.add(new CategoryEstimate(
                    category,
                    categoryMin.setScale(0, RoundingMode.HALF_UP),
                    categoryMax.setScale(0, RoundingMode.HALF_UP),
                    details
                ));
                totalMin = totalMin.add(categoryMin);
                totalMax = totalMax.add(categoryMax);

                log.info("Category {}: work €{}-€{}, material €{}-€{}, total €{}-€{}",
                    category, workMin.setScale(0, RoundingMode.HALF_UP), workMax.setScale(0, RoundingMode.HALF_UP),
                    materialMin.setScale(0, RoundingMode.HALF_UP), materialMax.setScale(0, RoundingMode.HALF_UP),
                    categoryMin.setScale(0, RoundingMode.HALF_UP), categoryMax.setScale(0, RoundingMode.HALF_UP));
            }
        }

        // If no specific quantities found, estimate based on keywords
        if (categoryEstimates.isEmpty()) {
            categoryEstimates = estimateFromKeywords(description, workPrices, materialPrices, bundlesByCategory, materialByName);
            for (CategoryEstimate ce : categoryEstimates) {
                totalMin = totalMin.add(ce.minPrice);
                totalMax = totalMax.add(ce.maxPrice);
            }
        }

        return new EstimateResult(
            totalMin.setScale(0, RoundingMode.HALF_UP),
            totalMax.setScale(0, RoundingMode.HALF_UP),
            categoryEstimates,
            null
        );
    }

    private BigDecimal calculateMaterialQuantity(QuantityInfo qty, WorkMaterialBundle bundle) {
        BigDecimal baseQty = BigDecimal.ZERO;
        String unitType = bundle.getUnitType().toLowerCase();

        switch (unitType) {
            case "m2":
                // Direct area-based: ratio is multiplier for area
                baseQty = BigDecimal.valueOf(qty.areaM2 > 0 ? qty.areaM2 : qty.count * 10);
                break;
            case "tk_per_m2":
                // Items per m2: ratio is items needed per m2
                double area = qty.areaM2 > 0 ? qty.areaM2 : qty.count * 10;
                baseQty = BigDecimal.valueOf(area);
                break;
            case "jm":
            case "jm_per_m2":
                // Linear meters
                baseQty = BigDecimal.valueOf(qty.lengthM > 0 ? qty.lengthM : qty.count * 3);
                break;
            case "m3_per_jm":
                // Cubic meters per linear meter (for foundations)
                double length = qty.lengthM > 0 ? qty.lengthM : qty.count * 10;
                baseQty = BigDecimal.valueOf(length);
                break;
            case "tk":
                // Count-based
                baseQty = BigDecimal.valueOf(qty.count > 0 ? qty.count : 1);
                break;
            default:
                baseQty = BigDecimal.valueOf(qty.areaM2 > 0 ? qty.areaM2 : qty.count);
        }

        return baseQty.multiply(bundle.getRatio()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getQuantityForWork(QuantityInfo qty) {
        // Prefer area, then count-based area estimate
        if (qty.areaM2 > 0) {
            return BigDecimal.valueOf(qty.areaM2);
        }
        if (qty.lengthM > 0) {
            return BigDecimal.valueOf(qty.lengthM);
        }
        // Estimate: each element ~10m2
        return BigDecimal.valueOf(qty.count * 10);
    }

    private Map<String, QuantityInfo> parseQuantities(String description) {
        Map<String, QuantityInfo> quantities = new HashMap<>();

        // Parse IFC element counts
        for (Map.Entry<String, String> entry : IFC_TO_CATEGORY.entrySet()) {
            String ifcType = entry.getKey();
            String category = entry.getValue();

            // Look for patterns like "IfcWall: 45" or "Seinad: 12 tk"
            Pattern pattern = Pattern.compile(ifcType + "[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(description);
            if (matcher.find()) {
                int count = Integer.parseInt(matcher.group(1));
                quantities.merge(category, new QuantityInfo(count, 0, 0, ifcType),
                    (old, newQ) -> new QuantityInfo(old.count + newQ.count, old.areaM2, old.lengthM, old.description + ", " + newQ.description));
            }
        }

        // Parse area measurements (m2, m²)
        Pattern areaPattern = Pattern.compile("(\\d+[,.]?\\d*)\\s*(?:m2|m²|ruutmeetrit?)", Pattern.CASE_INSENSITIVE);
        Matcher areaMatcher = areaPattern.matcher(description);
        while (areaMatcher.find()) {
            double area = parseNumber(areaMatcher.group(1));
            String context = getContext(description, areaMatcher.start(), 50);
            String category = determineCategoryFromContext(context);
            quantities.merge(category, new QuantityInfo(0, area, 0, "Pindala: " + area + " m²"),
                (old, newQ) -> new QuantityInfo(old.count, old.areaM2 + newQ.areaM2, old.lengthM, old.description));
        }

        // Parse linear measurements (jm, m, meetrit)
        Pattern lengthPattern = Pattern.compile("(\\d+[,.]?\\d*)\\s*(?:jm|jooksev\\s*meetrit?)", Pattern.CASE_INSENSITIVE);
        Matcher lengthMatcher = lengthPattern.matcher(description);
        while (lengthMatcher.find()) {
            double length = parseNumber(lengthMatcher.group(1));
            String context = getContext(description, lengthMatcher.start(), 50);
            String category = determineCategoryFromContext(context);
            quantities.merge(category, new QuantityInfo(0, 0, length, "Pikkus: " + length + " jm"),
                (old, newQ) -> new QuantityInfo(old.count, old.areaM2, old.lengthM + newQ.lengthM, old.description));
        }

        // Parse room counts
        Pattern roomPattern = Pattern.compile("(\\d+)\\s*(?:tuba|ruumi|magamistuba|vannituba|wc|köök)", Pattern.CASE_INSENSITIVE);
        Matcher roomMatcher = roomPattern.matcher(description);
        while (roomMatcher.find()) {
            int rooms = Integer.parseInt(roomMatcher.group(1));
            quantities.merge("Siseehitus", new QuantityInfo(rooms, rooms * 15, 0, "Ruumid: " + rooms),
                (old, newQ) -> new QuantityInfo(old.count + newQ.count, old.areaM2 + newQ.areaM2, old.lengthM, old.description));
        }

        // Detect wall area from wall count (estimate 10m² per wall)
        if (quantities.containsKey("Müüritööd") && quantities.get("Müüritööd").areaM2 == 0) {
            QuantityInfo wallQty = quantities.get("Müüritööd");
            quantities.put("Müüritööd", new QuantityInfo(wallQty.count, wallQty.count * 10, wallQty.lengthM, wallQty.description));
        }

        // Detect floor area from slab count (estimate 50m² per slab)
        if (quantities.containsKey("Vundament") && quantities.get("Vundament").areaM2 == 0) {
            QuantityInfo floorQty = quantities.get("Vundament");
            if (floorQty.count > 0) {
                quantities.put("Vundament", new QuantityInfo(floorQty.count, floorQty.count * 50, floorQty.lengthM, floorQty.description));
            }
        }

        return quantities;
    }

    private String determineCategoryFromContext(String context) {
        String lower = context.toLowerCase();
        if (lower.contains("plaat") && (lower.contains("vannitoa") || lower.contains("wc") || lower.contains("dušš"))) return "Plaatimine";
        if (lower.contains("sein") || lower.contains("wall") || lower.contains("fassaad")) return "Müüritööd";
        if (lower.contains("katus") || lower.contains("roof")) return "Katus";
        if (lower.contains("põrand") || lower.contains("floor") || lower.contains("slab")) return "Siseehitus";
        if (lower.contains("vundament") || lower.contains("foundation")) return "Vundament";
        if (lower.contains("terrass") || lower.contains("deck")) return "Terrass";
        if (lower.contains("plaat") || lower.contains("tile")) return "Plaatimine";
        if (lower.contains("toru") || lower.contains("pipe") || lower.contains("sanit")) return "Torutööd";
        if (lower.contains("elektr") || lower.contains("electric")) return "Elekter";
        return "Siseehitus";
    }

    private String getContext(String text, int position, int range) {
        int start = Math.max(0, position - range);
        int end = Math.min(text.length(), position + range);
        return text.substring(start, end);
    }

    private double parseNumber(String num) {
        return Double.parseDouble(num.replace(",", "."));
    }

    private List<CategoryEstimate> estimateFromKeywords(
            String description,
            List<WorkUnitPrice> workPrices,
            List<MaterialUnitPrice> materialPrices,
            Map<String, List<WorkMaterialBundle>> bundlesByCategory,
            Map<String, MaterialUnitPrice> materialByName) {

        List<CategoryEstimate> estimates = new ArrayList<>();
        String lowerDesc = description.toLowerCase();

        // Check for presence of each category and add base estimates
        Map<String, Integer> categoryMultipliers = new LinkedHashMap<>();

        if (lowerDesc.contains("sein") || lowerDesc.contains("wall") || lowerDesc.contains("müür")) {
            categoryMultipliers.put("Müüritööd", countOccurrences(lowerDesc, "sein", "wall", "müür"));
        }
        if (lowerDesc.contains("katus") || lowerDesc.contains("roof") || lowerDesc.contains("katusekivi")) {
            categoryMultipliers.put("Katus", countOccurrences(lowerDesc, "katus", "roof"));
        }
        if (lowerDesc.contains("põrand") || lowerDesc.contains("floor") || lowerDesc.contains("parkett")) {
            categoryMultipliers.put("Siseehitus", countOccurrences(lowerDesc, "põrand", "floor", "parkett"));
        }
        if (lowerDesc.contains("vundament") || lowerDesc.contains("foundation") || lowerDesc.contains("betoon")) {
            categoryMultipliers.put("Vundament", countOccurrences(lowerDesc, "vundament", "foundation", "betoon"));
        }
        if (lowerDesc.contains("viimistl") || lowerDesc.contains("värv") || lowerDesc.contains("pahtel")) {
            categoryMultipliers.put("Viimistlus", countOccurrences(lowerDesc, "viimistl", "värv", "pahtel"));
        }
        if (lowerDesc.contains("plaat") || lowerDesc.contains("tile") || lowerDesc.contains("vannitoa")) {
            categoryMultipliers.put("Plaatimine", countOccurrences(lowerDesc, "plaat", "tile", "vannitoa"));
        }
        if (lowerDesc.contains("toru") || lowerDesc.contains("pipe") || lowerDesc.contains("sanit") || lowerDesc.contains("wc")) {
            categoryMultipliers.put("Torutööd", countOccurrences(lowerDesc, "toru", "pipe", "sanit", "wc"));
        }
        if (lowerDesc.contains("elektr") || lowerDesc.contains("pistik") || lowerDesc.contains("lüliti")) {
            categoryMultipliers.put("Elekter", countOccurrences(lowerDesc, "elektr", "pistik", "lüliti"));
        }
        if (lowerDesc.contains("terrass") || lowerDesc.contains("deck") || lowerDesc.contains("rõdu")) {
            categoryMultipliers.put("Terrass", countOccurrences(lowerDesc, "terrass", "deck", "rõdu"));
        }
        if (lowerDesc.contains("uks") || lowerDesc.contains("door") || lowerDesc.contains("aken") || lowerDesc.contains("window")) {
            categoryMultipliers.put("Siseehitus", categoryMultipliers.getOrDefault("Siseehitus", 0) + countOccurrences(lowerDesc, "uks", "door", "aken", "window"));
        }

        // If no keywords found, add generic estimate
        if (categoryMultipliers.isEmpty()) {
            categoryMultipliers.put("Siseehitus", 1);
            categoryMultipliers.put("Viimistlus", 1);
        }

        // Map work prices by category
        Map<String, List<WorkUnitPrice>> workByCategory = workPrices.stream()
            .collect(Collectors.groupingBy(WorkUnitPrice::getCategory));

        // Calculate estimates based on multipliers
        for (Map.Entry<String, Integer> entry : categoryMultipliers.entrySet()) {
            String category = entry.getKey();
            int multiplier = Math.max(1, Math.min(entry.getValue(), 10)); // Cap at 10
            double estimatedArea = multiplier * 20; // Estimate 20m2 per keyword occurrence

            QuantityInfo qty = new QuantityInfo(multiplier, estimatedArea, 0, "Hinnanguline");

            BigDecimal workMin = BigDecimal.ZERO;
            BigDecimal workMax = BigDecimal.ZERO;
            BigDecimal materialMin = BigDecimal.ZERO;
            BigDecimal materialMax = BigDecimal.ZERO;

            // Work cost
            List<WorkUnitPrice> categoryWork = workByCategory.get(category);
            if (categoryWork != null && !categoryWork.isEmpty()) {
                WorkUnitPrice wp = categoryWork.get(0);
                BigDecimal workQty = BigDecimal.valueOf(estimatedArea);
                workMin = wp.getMinPriceEur().multiply(workQty);
                workMax = wp.getMaxPriceEur().multiply(workQty);
            }

            // Material cost using bundles
            List<WorkMaterialBundle> categoryBundles = bundlesByCategory.get(category);
            if (categoryBundles != null) {
                for (WorkMaterialBundle bundle : categoryBundles) {
                    if (bundle.getRatio().compareTo(BigDecimal.ZERO) <= 0) continue;

                    MaterialUnitPrice material = materialByName.get(bundle.getMaterialName());
                    if (material == null) continue;

                    BigDecimal materialQty = calculateMaterialQuantity(qty, bundle);
                    if (materialQty.compareTo(BigDecimal.ZERO) > 0) {
                        materialMin = materialMin.add(material.getMinPriceEur().multiply(materialQty));
                        materialMax = materialMax.add(material.getMaxPriceEur().multiply(materialQty));
                    }
                }
            }

            BigDecimal catMin = workMin.add(materialMin);
            BigDecimal catMax = workMax.add(materialMax);

            if (catMin.compareTo(BigDecimal.ZERO) > 0) {
                estimates.add(new CategoryEstimate(
                    category,
                    catMin.setScale(0, RoundingMode.HALF_UP),
                    catMax.setScale(0, RoundingMode.HALF_UP),
                    "Hinnanguline (töö + materjal)"
                ));
            }
        }

        return estimates;
    }

    private int countOccurrences(String text, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            int idx = 0;
            while ((idx = text.indexOf(keyword, idx)) != -1) {
                count++;
                idx += keyword.length();
            }
        }
        return count;
    }

    // DTOs
    public record EstimateResult(
        BigDecimal minTotal,
        BigDecimal maxTotal,
        List<CategoryEstimate> categories,
        String error
    ) {
        public String getFormattedRange() {
            if (minTotal.compareTo(BigDecimal.ZERO) == 0 && maxTotal.compareTo(BigDecimal.ZERO) == 0) {
                return "Hinnangut ei saa arvutada";
            }
            return String.format("€%,d – €%,d", minTotal.intValue(), maxTotal.intValue());
        }
    }

    public record CategoryEstimate(
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String details
    ) {}

    private record QuantityInfo(int count, double areaM2, double lengthM, String description) {}
}
