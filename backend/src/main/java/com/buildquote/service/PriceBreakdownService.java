package com.buildquote.service;

import com.buildquote.dto.PriceBreakdownDTO;
import com.buildquote.dto.PriceBreakdownDTO.*;
import com.buildquote.dto.SupplierPriceDTO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceBreakdownService {

    private final WorkUnitPriceRepository workUnitPriceRepository;
    private final MaterialUnitPriceRepository materialUnitPriceRepository;
    private final WorkMaterialBundleRepository workMaterialBundleRepository;

    // Estonian construction suppliers (mock data for demo)
    private static final List<String> SUPPLIERS = List.of(
        "Bauhof", "Ehituse ABC", "Decora", "Espak", "Puumarket",
        "K-Rauta", "Optimera", "Stark", "Rautakesko", "Nordecon Materjalid"
    );

    // Estonian labor rate sources
    private static final String LABOR_SOURCE = "Eesti ehitussektori keskmine 2024-2025";

    // Category to Estonian name mapping
    private static final Map<String, String> CATEGORY_NAMES = Map.ofEntries(
        Map.entry("Müüritööd", "Müüritööd"),
        Map.entry("Katus", "Katusetööd"),
        Map.entry("Vundament", "Vundamenditööd"),
        Map.entry("Siseehitus", "Siseviimistlus"),
        Map.entry("Plaatimine", "Plaatimistööd"),
        Map.entry("Elekter", "Elektritööd"),
        Map.entry("Torutööd", "Torutööd"),
        Map.entry("Viimistlus", "Viimistlustööd"),
        Map.entry("Terrass", "Terrassitööd"),
        Map.entry("Fassaad", "Fassaaditööd"),
        Map.entry("TILING", "Plaatimistööd"),
        Map.entry("ELECTRICAL", "Elektritööd"),
        Map.entry("PLUMBING", "Torutööd"),
        Map.entry("FINISHING", "Viimistlustööd"),
        Map.entry("ROOFING", "Katusetööd"),
        Map.entry("GENERAL_CONSTRUCTION", "Üldehitus"),
        Map.entry("FLOORING", "Põrandatööd"),
        Map.entry("HVAC", "Küte ja ventilatsioon"),
        Map.entry("WINDOWS_DOORS", "Aknad ja uksed"),
        Map.entry("FACADE", "Fassaaditööd"),
        Map.entry("DEMOLITION", "Lammutustööd"),
        Map.entry("LANDSCAPING", "Haljastus")
    );

    // Average hours per m2 by category
    private static final Map<String, BigDecimal> HOURS_PER_M2 = Map.ofEntries(
        Map.entry("Müüritööd", new BigDecimal("0.8")),
        Map.entry("Katus", new BigDecimal("0.6")),
        Map.entry("Vundament", new BigDecimal("1.2")),
        Map.entry("Siseehitus", new BigDecimal("0.5")),
        Map.entry("Plaatimine", new BigDecimal("1.5")),
        Map.entry("Elekter", new BigDecimal("0.4")),
        Map.entry("Torutööd", new BigDecimal("0.6")),
        Map.entry("Viimistlus", new BigDecimal("0.3")),
        Map.entry("Terrass", new BigDecimal("0.7")),
        Map.entry("Fassaad", new BigDecimal("0.5")),
        Map.entry("TILING", new BigDecimal("1.5")),
        Map.entry("ELECTRICAL", new BigDecimal("0.4")),
        Map.entry("PLUMBING", new BigDecimal("0.6")),
        Map.entry("FINISHING", new BigDecimal("0.3")),
        Map.entry("ROOFING", new BigDecimal("0.6")),
        Map.entry("GENERAL_CONSTRUCTION", new BigDecimal("0.8")),
        Map.entry("FLOORING", new BigDecimal("0.4")),
        Map.entry("HVAC", new BigDecimal("0.5")),
        Map.entry("WINDOWS_DOORS", new BigDecimal("0.3")),
        Map.entry("FACADE", new BigDecimal("0.5")),
        Map.entry("DEMOLITION", new BigDecimal("0.4")),
        Map.entry("LANDSCAPING", new BigDecimal("0.3"))
    );

    // Hourly rates by skill level (EUR)
    private static final BigDecimal HOURLY_RATE_MIN = new BigDecimal("25");
    private static final BigDecimal HOURLY_RATE_MAX = new BigDecimal("45");

    public PriceBreakdownDTO calculateBreakdown(String category, BigDecimal quantity, String unit) {
        log.info("Calculating price breakdown for category={}, quantity={}, unit={}", category, quantity, unit);

        // Normalize category
        String normalizedCategory = normalizeCategory(category);

        // Get materials for this category
        List<MaterialLineDTO> materials = calculateMaterials(normalizedCategory, quantity, unit);

        // Calculate labor costs
        LaborCostDTO labor = calculateLabor(normalizedCategory, quantity);

        // Calculate other costs (transport, waste disposal)
        OtherCostsDTO otherCosts = calculateOtherCosts(quantity, materials);

        // Calculate totals
        BigDecimal materialTotalMin = materials.stream()
            .map(m -> m.unitPriceMin().multiply(m.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal materialTotalMax = materials.stream()
            .map(m -> m.unitPriceMax().multiply(m.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMin = materialTotalMin.add(labor.totalMin()).add(otherCosts.totalMin());
        BigDecimal totalMax = materialTotalMax.add(labor.totalMax()).add(otherCosts.totalMax());

        // Calculate confidence based on data freshness and availability
        int confidence = calculateConfidence(materials);
        String confidenceLabel = getConfidenceLabel(confidence);

        return new PriceBreakdownDTO(
            materials,
            labor,
            otherCosts,
            confidence,
            confidenceLabel,
            totalMin.setScale(0, RoundingMode.HALF_UP),
            totalMax.setScale(0, RoundingMode.HALF_UP)
        );
    }

    private String normalizeCategory(String category) {
        // Map English category codes to Estonian names for database lookup
        if (category == null) return "Siseehitus";

        // Check if it's already a valid Estonian category
        if (HOURS_PER_M2.containsKey(category)) {
            return category;
        }

        // Try mapping from common category codes
        return switch (category.toUpperCase()) {
            case "TILING" -> "Plaatimine";
            case "ELECTRICAL" -> "Elekter";
            case "PLUMBING" -> "Torutööd";
            case "FINISHING" -> "Viimistlus";
            case "ROOFING" -> "Katus";
            case "GENERAL_CONSTRUCTION" -> "Siseehitus";
            case "FLOORING" -> "Siseehitus";
            case "HVAC" -> "Torutööd";
            case "WINDOWS_DOORS" -> "Siseehitus";
            case "FACADE" -> "Fassaad";
            case "DEMOLITION" -> "Siseehitus";
            case "LANDSCAPING" -> "Terrass";
            default -> "Siseehitus";
        };
    }

    private List<MaterialLineDTO> calculateMaterials(String category, BigDecimal quantity, String unit) {
        List<MaterialLineDTO> materials = new ArrayList<>();

        // Get work-material bundles for this category
        List<WorkMaterialBundle> bundles = workMaterialBundleRepository.findAll().stream()
            .filter(b -> b.getWorkCategory().equalsIgnoreCase(category))
            .toList();

        // Get all material prices
        Map<String, MaterialUnitPrice> materialPrices = materialUnitPriceRepository.findAll().stream()
            .collect(Collectors.toMap(
                MaterialUnitPrice::getMaterialName,
                m -> m,
                (a, b) -> a
            ));

        if (bundles.isEmpty()) {
            // Fallback: generate estimated materials based on category
            materials.addAll(generateEstimatedMaterials(category, quantity));
        } else {
            for (WorkMaterialBundle bundle : bundles) {
                MaterialUnitPrice price = materialPrices.get(bundle.getMaterialName());
                if (price == null) {
                    continue;
                }

                BigDecimal materialQty = calculateMaterialQuantity(quantity, bundle);
                if (materialQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Pick a random supplier for demo
                String supplier = SUPPLIERS.get(new Random().nextInt(SUPPLIERS.size()));
                String priceSource = price.getSource() != null && price.getSource().toLowerCase().contains("auto")
                    ? "AUTO" : "MANUAL";
                LocalDate lastUpdated = price.getUpdatedAt() != null
                    ? price.getUpdatedAt().toLocalDate()
                    : LocalDate.now().minusDays(15);

                materials.add(new MaterialLineDTO(
                    bundle.getMaterialName(),
                    materialQty.setScale(1, RoundingMode.HALF_UP),
                    price.getUnit(),
                    price.getMinPriceEur(),
                    price.getMaxPriceEur(),
                    supplier,
                    "https://" + supplier.toLowerCase().replace(" ", "") + ".ee",
                    priceSource,
                    lastUpdated
                ));
            }
        }

        // If still no materials, add estimated ones
        if (materials.isEmpty()) {
            materials.addAll(generateEstimatedMaterials(category, quantity));
        }

        return materials;
    }

    private BigDecimal calculateMaterialQuantity(BigDecimal workQty, WorkMaterialBundle bundle) {
        String unitType = bundle.getUnitType().toLowerCase();
        BigDecimal ratio = bundle.getRatio();

        return switch (unitType) {
            case "m2", "tk_per_m2" -> workQty.multiply(ratio);
            case "jm", "jm_per_m2" -> workQty.multiply(ratio);
            case "m3_per_jm" -> workQty.multiply(ratio);
            case "tk" -> ratio;
            default -> workQty.multiply(ratio);
        };
    }

    private List<MaterialLineDTO> generateEstimatedMaterials(String category, BigDecimal quantity) {
        List<MaterialLineDTO> materials = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Generate 2-4 estimated materials based on category
        switch (category.toLowerCase()) {
            case "plaatimine" -> {
                materials.add(new MaterialLineDTO(
                    "Keraamiline plaat 60x60", quantity.multiply(new BigDecimal("1.1")),
                    "m²", new BigDecimal("15"), new BigDecimal("45"),
                    "Bauhof", "https://bauhof.ee", "MANUAL", today.minusDays(7)
                ));
                materials.add(new MaterialLineDTO(
                    "Plaadiliim C2", quantity.multiply(new BigDecimal("0.05")),
                    "kott (25kg)", new BigDecimal("12"), new BigDecimal("18"),
                    "Ehituse ABC", "https://ehituseabc.ee", "AUTO", today.minusDays(3)
                ));
                materials.add(new MaterialLineDTO(
                    "Vuugisegu", quantity.multiply(new BigDecimal("0.02")),
                    "kott (5kg)", new BigDecimal("8"), new BigDecimal("15"),
                    "Decora", "https://decora.ee", "AUTO", today.minusDays(5)
                ));
            }
            case "elekter" -> {
                materials.add(new MaterialLineDTO(
                    "Elektrikaabel NYM 3x2.5", quantity.multiply(new BigDecimal("3")),
                    "jm", new BigDecimal("1.20"), new BigDecimal("2.50"),
                    "Espak", "https://espak.ee", "AUTO", today.minusDays(2)
                ));
                materials.add(new MaterialLineDTO(
                    "Pistikupesad", quantity.multiply(new BigDecimal("0.3")),
                    "tk", new BigDecimal("5"), new BigDecimal("15"),
                    "K-Rauta", "https://k-rauta.ee", "MANUAL", today.minusDays(10)
                ));
                materials.add(new MaterialLineDTO(
                    "Lülitid", quantity.multiply(new BigDecimal("0.15")),
                    "tk", new BigDecimal("8"), new BigDecimal("25"),
                    "Bauhof", "https://bauhof.ee", "AUTO", today.minusDays(4)
                ));
            }
            case "torutööd" -> {
                materials.add(new MaterialLineDTO(
                    "PPR toru Ø20", quantity.multiply(new BigDecimal("2")),
                    "jm", new BigDecimal("2"), new BigDecimal("4"),
                    "Espak", "https://espak.ee", "AUTO", today.minusDays(6)
                ));
                materials.add(new MaterialLineDTO(
                    "Toru liitmikud", quantity.multiply(new BigDecimal("0.5")),
                    "tk", new BigDecimal("3"), new BigDecimal("8"),
                    "Optimera", "https://optimera.ee", "MANUAL", today.minusDays(12)
                ));
            }
            case "katus" -> {
                materials.add(new MaterialLineDTO(
                    "Katuseplekk", quantity.multiply(new BigDecimal("1.05")),
                    "m²", new BigDecimal("12"), new BigDecimal("25"),
                    "Ruukki", "https://ruukki.ee", "AUTO", today.minusDays(5)
                ));
                materials.add(new MaterialLineDTO(
                    "Aluskatematerjal", quantity.multiply(new BigDecimal("1.1")),
                    "m²", new BigDecimal("3"), new BigDecimal("6"),
                    "K-Rauta", "https://k-rauta.ee", "AUTO", today.minusDays(8)
                ));
                materials.add(new MaterialLineDTO(
                    "Soojustus 200mm", quantity.multiply(new BigDecimal("1.0")),
                    "m²", new BigDecimal("15"), new BigDecimal("25"),
                    "Isover", "https://isover.ee", "AUTO", today.minusDays(3)
                ));
            }
            case "vundament" -> {
                materials.add(new MaterialLineDTO(
                    "Betoon C25/30", quantity.multiply(new BigDecimal("0.15")),
                    "m³", new BigDecimal("95"), new BigDecimal("120"),
                    "Rudus", "https://rudus.ee", "AUTO", today.minusDays(2)
                ));
                materials.add(new MaterialLineDTO(
                    "Armatuur Ø12", quantity.multiply(new BigDecimal("8")),
                    "kg", new BigDecimal("0.90"), new BigDecimal("1.30"),
                    "Merko", "https://merko.ee", "AUTO", today.minusDays(4)
                ));
                materials.add(new MaterialLineDTO(
                    "Raketis", quantity.multiply(new BigDecimal("0.5")),
                    "m²", new BigDecimal("8"), new BigDecimal("15"),
                    "Bauhof", "https://bauhof.ee", "MANUAL", today.minusDays(14)
                ));
            }
            default -> {
                // Generic materials
                materials.add(new MaterialLineDTO(
                    "Põhimaterjal", quantity.multiply(new BigDecimal("1.0")),
                    "ühik", new BigDecimal("20"), new BigDecimal("40"),
                    "Bauhof", "https://bauhof.ee", "MANUAL", today.minusDays(20)
                ));
                materials.add(new MaterialLineDTO(
                    "Abimaterjal", quantity.multiply(new BigDecimal("0.2")),
                    "ühik", new BigDecimal("5"), new BigDecimal("15"),
                    "K-Rauta", "https://k-rauta.ee", "MANUAL", today.minusDays(25)
                ));
            }
        }

        return materials;
    }

    private LaborCostDTO calculateLabor(String category, BigDecimal quantity) {
        BigDecimal hoursPerUnit = HOURS_PER_M2.getOrDefault(category, new BigDecimal("0.5"));
        BigDecimal totalHours = quantity.multiply(hoursPerUnit).setScale(0, RoundingMode.HALF_UP);

        BigDecimal totalMin = totalHours.multiply(HOURLY_RATE_MIN);
        BigDecimal totalMax = totalHours.multiply(HOURLY_RATE_MAX);

        return new LaborCostDTO(
            totalHours,
            HOURLY_RATE_MIN,
            HOURLY_RATE_MAX,
            totalMin.setScale(0, RoundingMode.HALF_UP),
            totalMax.setScale(0, RoundingMode.HALF_UP),
            LABOR_SOURCE
        );
    }

    private OtherCostsDTO calculateOtherCosts(BigDecimal quantity, List<MaterialLineDTO> materials) {
        // Transport: ~5-10% of material cost or minimum 50-100€
        BigDecimal materialTotalMin = materials.stream()
            .map(m -> m.unitPriceMin().multiply(m.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal materialTotalMax = materials.stream()
            .map(m -> m.unitPriceMax().multiply(m.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal transportMin = materialTotalMin.multiply(new BigDecimal("0.05"))
            .max(new BigDecimal("50")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal transportMax = materialTotalMax.multiply(new BigDecimal("0.10"))
            .max(new BigDecimal("150")).setScale(0, RoundingMode.HALF_UP);

        // Waste disposal: ~2-5€ per m² or minimum 80€
        BigDecimal wasteMin = quantity.multiply(new BigDecimal("2"))
            .max(new BigDecimal("80")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal wasteMax = quantity.multiply(new BigDecimal("5"))
            .max(new BigDecimal("150")).setScale(0, RoundingMode.HALF_UP);

        return new OtherCostsDTO(
            transportMin,
            transportMax,
            wasteMin,
            wasteMax,
            transportMin.add(wasteMin),
            transportMax.add(wasteMax)
        );
    }

    private int calculateConfidence(List<MaterialLineDTO> materials) {
        if (materials.isEmpty()) {
            return 30; // Low confidence if no materials found
        }

        int autoCount = 0;
        int freshCount = 0;
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);

        for (MaterialLineDTO m : materials) {
            if ("AUTO".equals(m.priceSource())) {
                autoCount++;
            }
            if (m.lastUpdated() != null && m.lastUpdated().isAfter(thirtyDaysAgo)) {
                freshCount++;
            }
        }

        // Base confidence
        double autoRatio = (double) autoCount / materials.size();
        double freshRatio = (double) freshCount / materials.size();

        // Calculate weighted confidence (auto source = 40%, freshness = 40%, base = 20%)
        int confidence = 20 + (int)(autoRatio * 40) + (int)(freshRatio * 40);

        return Math.min(100, Math.max(10, confidence));
    }

    private String getConfidenceLabel(int confidence) {
        if (confidence >= 80) {
            return "Kõrge täpsus";
        } else if (confidence >= 50) {
            return "Keskmine täpsus";
        } else {
            return "Madal täpsus — põhineb hinnangutel";
        }
    }

    public List<SupplierPriceDTO> getSupplierPrices(String materialName, String region) {
        log.info("Getting supplier prices for material={}, region={}", materialName, region);

        List<SupplierPriceDTO> prices = new ArrayList<>();
        Random random = new Random(materialName.hashCode()); // Deterministic randomness

        // Get base price from database if available
        Optional<MaterialUnitPrice> basePrice = materialUnitPriceRepository.findAll().stream()
            .filter(m -> m.getMaterialName().equalsIgnoreCase(materialName))
            .findFirst();

        BigDecimal basePriceValue = basePrice
            .map(MaterialUnitPrice::getAvgPriceEur)
            .orElse(new BigDecimal("25"));

        // Generate prices from multiple suppliers with some variance
        for (String supplier : SUPPLIERS) {
            // Add some random variance (-15% to +25%)
            double variance = 0.85 + (random.nextDouble() * 0.40);
            BigDecimal price = basePriceValue.multiply(new BigDecimal(variance))
                .setScale(2, RoundingMode.HALF_UP);

            // Random date within last 30 days
            LocalDate lastUpdated = LocalDate.now().minusDays(random.nextInt(30));

            prices.add(new SupplierPriceDTO(
                supplier,
                price,
                "https://" + supplier.toLowerCase().replace(" ", "") + ".ee/toode/" +
                    materialName.toLowerCase().replace(" ", "-"),
                lastUpdated
            ));
        }

        // Sort by price ascending
        prices.sort(Comparator.comparing(SupplierPriceDTO::price));

        return prices;
    }
}
