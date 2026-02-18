package com.buildquote.pipeline.handlers;

import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ValidateParseStepHandler implements StepHandler {

    private final ProjectRepository projectRepository;

    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("500000");
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.6;

    // Known category enum values (matching SupplierSearchService CATEGORY_TO_EMTAK)
    private static final Set<String> KNOWN_CATEGORIES = Set.of(
            "GENERAL_CONSTRUCTION", "ELECTRICAL", "PLUMBING", "TILING",
            "FINISHING", "ROOFING", "FACADE", "LANDSCAPING",
            "WINDOWS_DOORS", "HVAC", "FLOORING", "DEMOLITION"
    );

    // EMTAK code mappings (from SupplierSearchService)
    private static final Map<String, String> CATEGORY_TO_EMTAK = Map.ofEntries(
            Map.entry("GENERAL_CONSTRUCTION", "4120"),
            Map.entry("ELECTRICAL", "4321"),
            Map.entry("PLUMBING", "4322"),
            Map.entry("TILING", "4333"),
            Map.entry("FINISHING", "4334"),
            Map.entry("ROOFING", "4391"),
            Map.entry("FACADE", "4399"),
            Map.entry("LANDSCAPING", "43331"),
            Map.entry("WINDOWS_DOORS", "4332"),
            Map.entry("HVAC", "43221"),
            Map.entry("FLOORING", "43332"),
            Map.entry("DEMOLITION", "4311")
    );

    // Sane max quantity bounds per unit type
    private static final Map<String, BigDecimal> MAX_QUANTITY_BY_UNIT = Map.of(
            "m2", new BigDecimal("100000"),
            "m3", new BigDecimal("50000"),
            "jm", new BigDecimal("100000"),
            "tk", new BigDecimal("10000"),
            "km", new BigDecimal("1000"),
            "komplekt", new BigDecimal("1000"),
            "kpl", new BigDecimal("1000")
    );

    @Override
    public String getStepType() {
        return "VALIDATE_PARSE";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        UUID projectId = context.getProjectId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<ProjectStage> stages = project.getStages();
        if (stages == null || stages.isEmpty()) {
            return StepResult.failed("No stages found in parsed project");
        }

        List<String> projectIssues = new ArrayList<>();
        double totalConfidence = 0;
        int stageCount = 0;

        Set<String> seenStageKeys = new HashSet<>();

        for (ProjectStage stage : stages) {
            List<String> stageIssues = new ArrayList<>();
            double stageConfidence = 1.0;

            // 1. Category is a known enum value
            if (stage.getCategory() == null || stage.getCategory().isBlank()) {
                stageIssues.add("Missing category");
                stageConfidence -= 0.3;
            } else if (!KNOWN_CATEGORIES.contains(stage.getCategory())) {
                stageIssues.add("Unknown category: " + stage.getCategory());
                stageConfidence -= 0.2;
            }

            // 2. Quantity positive and within sane bounds per unit type
            if (stage.getQuantity() == null || stage.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                stageIssues.add("Invalid quantity: must be positive");
                stageConfidence -= 0.2;
            } else if (stage.getUnit() != null) {
                BigDecimal maxQty = MAX_QUANTITY_BY_UNIT.getOrDefault(
                        stage.getUnit().toLowerCase(), new BigDecimal("100000"));
                if (stage.getQuantity().compareTo(maxQty) > 0) {
                    stageIssues.add("Quantity " + stage.getQuantity() + " " + stage.getUnit() + " exceeds sane bounds (" + maxQty + ")");
                    stageConfidence -= 0.15;
                }
            }

            // 3. Price estimates: exist and min <= median <= max
            if (stage.getPriceEstimateMin() == null || stage.getPriceEstimateMax() == null) {
                stageIssues.add("Missing price estimates");
                stageConfidence -= 0.15;
            } else {
                if (stage.getPriceEstimateMin().compareTo(BigDecimal.ZERO) < 0) {
                    stageIssues.add("Negative min price estimate");
                    stageConfidence -= 0.1;
                }
                if (stage.getPriceEstimateMedian() != null) {
                    if (stage.getPriceEstimateMin().compareTo(stage.getPriceEstimateMedian()) > 0) {
                        stageIssues.add("Min price > median price");
                        stageConfidence -= 0.1;
                    }
                    if (stage.getPriceEstimateMedian().compareTo(stage.getPriceEstimateMax()) > 0) {
                        stageIssues.add("Median price > max price");
                        stageConfidence -= 0.1;
                    }
                }
                // Unit price check
                if (stage.getQuantity() != null && stage.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal unitPrice = stage.getPriceEstimateMax().divide(
                            stage.getQuantity(), 2, java.math.RoundingMode.HALF_UP);
                    if (unitPrice.compareTo(MAX_UNIT_PRICE) > 0) {
                        stageIssues.add("Unit price " + unitPrice + " exceeds max bound " + MAX_UNIT_PRICE);
                        stageConfidence -= 0.15;
                    }
                }
            }

            // 4. No duplicate stage names with identical categories
            String stageKey = (stage.getName() + "|" + stage.getCategory()).toLowerCase();
            if (!seenStageKeys.add(stageKey)) {
                stageIssues.add("Duplicate stage: same name and category");
                stageConfidence -= 0.2;
            }

            // 5. EMTAK code mapping
            if (stage.getCategory() != null && CATEGORY_TO_EMTAK.containsKey(stage.getCategory())) {
                stage.setEmtakCode(CATEGORY_TO_EMTAK.get(stage.getCategory()));
            }

            // 6. Location check
            if (project.getLocation() == null || project.getLocation().isBlank()
                    || "Tallinn".equals(project.getLocation())) {
                // Only flag as issue if literally no location was provided
                if (project.getLocation() == null || project.getLocation().isBlank()) {
                    stageIssues.add("No location extracted - defaulting");
                    stageConfidence -= 0.05;
                }
            }

            // Clamp confidence
            stageConfidence = Math.max(0.0, Math.min(1.0, stageConfidence));

            stage.setValidationConfidence(BigDecimal.valueOf(stageConfidence));
            stage.setValidationIssues(stageIssues.isEmpty() ? null : String.join("; ", stageIssues));

            totalConfidence += stageConfidence;
            stageCount++;
            projectIssues.addAll(stageIssues);
        }

        double avgConfidence = stageCount > 0 ? totalConfidence / stageCount : 0;
        project.setParseConfidence(BigDecimal.valueOf(avgConfidence));

        if (avgConfidence >= MIN_CONFIDENCE_THRESHOLD) {
            project.setValidationStatus("PASSED");
            projectRepository.save(project);
            log.info("Validation passed for project {} with confidence {}", projectId, avgConfidence);

            context.put("validationConfidence", avgConfidence);
            context.put("validationIssues", projectIssues);
            return StepResult.success(Map.of(
                    "confidence", avgConfidence,
                    "stageCount", stageCount,
                    "issueCount", projectIssues.size()
            ));
        } else {
            project.setValidationStatus("NEEDS_REVIEW");
            projectRepository.save(project);
            log.warn("Validation below threshold for project {}: confidence={}, issues={}",
                    projectId, avgConfidence, projectIssues.size());

            context.put("validationConfidence", avgConfidence);
            context.put("validationIssues", projectIssues);
            return StepResult.awaiting(String.format(
                    "Parse confidence %.0f%% below threshold (60%%). %d issues found. Human review required.",
                    avgConfidence * 100, projectIssues.size()));
        }
    }

    @Override
    public boolean canRetry() {
        return false;
    }
}
