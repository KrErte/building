package com.buildquote.service;

import com.buildquote.dto.DependentMaterialDto;
import com.buildquote.dto.ProjectStageDto;
import com.buildquote.entity.ComponentRecipe;
import com.buildquote.entity.MaterialUnitPrice;
import com.buildquote.repository.ComponentRecipeRepository;
import com.buildquote.repository.MaterialUnitPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DependentMaterialService {

    private final ComponentRecipeRepository recipeRepository;
    private final MaterialUnitPriceRepository materialUnitPriceRepository;

    /**
     * Calculate dependent materials for all detected stages.
     * For each stage: find matching recipes by name (substring), then by category.
     * Multiply recipe quantities by stage quantity, aggregate identical materials,
     * and look up prices.
     */
    public List<DependentMaterialDto> calculateDependentMaterials(List<ProjectStageDto> stages) {
        if (stages == null || stages.isEmpty()) {
            return Collections.emptyList();
        }

        List<ComponentRecipe> allRecipes = recipeRepository.findAll();
        if (allRecipes.isEmpty()) {
            log.info("No component recipes configured, skipping dependent material calculation");
            return Collections.emptyList();
        }

        // materialKey (name+unit) -> aggregated material
        Map<String, DependentMaterialDto> aggregated = new LinkedHashMap<>();

        for (ProjectStageDto stage : stages) {
            List<ComponentRecipe> matchedRecipes = findMatchingRecipes(stage, allRecipes);

            for (ComponentRecipe recipe : matchedRecipes) {
                BigDecimal calculatedQty = stage.getQuantity().multiply(recipe.getQuantityPerUnit());
                String key = recipe.getMaterialName().toLowerCase() + "|" + recipe.getMaterialUnit().toLowerCase();

                DependentMaterialDto material = aggregated.computeIfAbsent(key, k -> {
                    DependentMaterialDto dto = new DependentMaterialDto();
                    dto.setMaterialName(recipe.getMaterialName());
                    dto.setUnit(recipe.getMaterialUnit());
                    dto.setTotalQuantity(BigDecimal.ZERO);
                    return dto;
                });

                material.setTotalQuantity(material.getTotalQuantity().add(calculatedQty));

                if (!material.getSourceStages().contains(stage.getName())) {
                    material.getSourceStages().add(stage.getName());
                }
            }
        }

        // Look up prices for each aggregated material
        List<MaterialUnitPrice> allPrices = materialUnitPriceRepository.findAll();

        for (DependentMaterialDto material : aggregated.values()) {
            lookupPrice(material, allPrices);
        }

        List<DependentMaterialDto> result = new ArrayList<>(aggregated.values());
        log.info("Calculated {} dependent materials from {} stages", result.size(), stages.size());
        return result;
    }

    /**
     * Find matching recipes for a stage.
     * 1. Try name substring match (stage name contains recipe componentName)
     * 2. If no name matches, fall back to category match
     */
    private List<ComponentRecipe> findMatchingRecipes(ProjectStageDto stage, List<ComponentRecipe> allRecipes) {
        String stageName = stage.getName().toLowerCase();

        // First: exact name substring match
        List<ComponentRecipe> nameMatches = new ArrayList<>();
        for (ComponentRecipe recipe : allRecipes) {
            if (stageName.contains(recipe.getComponentName().toLowerCase())) {
                nameMatches.add(recipe);
            }
        }

        if (!nameMatches.isEmpty()) {
            return nameMatches;
        }

        // Fallback: match by category
        if (stage.getCategory() != null) {
            List<ComponentRecipe> categoryMatches = new ArrayList<>();
            for (ComponentRecipe recipe : allRecipes) {
                if (recipe.getComponentCategory() != null &&
                    recipe.getComponentCategory().equalsIgnoreCase(stage.getCategory())) {
                    categoryMatches.add(recipe);
                }
            }
            return categoryMatches;
        }

        return Collections.emptyList();
    }

    /**
     * Look up material price from MaterialUnitPrice table.
     * Uses fuzzy name matching.
     */
    private void lookupPrice(DependentMaterialDto material, List<MaterialUnitPrice> allPrices) {
        String materialNameLower = material.getMaterialName().toLowerCase();

        // Try to find a price match
        MaterialUnitPrice bestMatch = null;
        int bestScore = 0;

        for (MaterialUnitPrice price : allPrices) {
            String priceName = price.getMaterialName().toLowerCase();

            // Exact match
            if (priceName.equals(materialNameLower)) {
                bestMatch = price;
                break;
            }

            // Substring match - check both directions
            int score = 0;
            if (priceName.contains(materialNameLower) || materialNameLower.contains(priceName)) {
                score = Math.min(priceName.length(), materialNameLower.length());
            }

            // Check for significant word overlap
            if (score == 0) {
                String[] materialWords = materialNameLower.split("\\s+");
                for (String word : materialWords) {
                    if (word.length() >= 3 && priceName.contains(word)) {
                        score += word.length();
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = price;
            }
        }

        if (bestMatch != null && bestScore >= 3) {
            material.setUnitPriceMin(bestMatch.getMinPriceEur());
            material.setUnitPriceMax(bestMatch.getMaxPriceEur());

            if (bestMatch.getMinPriceEur() != null) {
                material.setTotalPriceMin(
                    material.getTotalQuantity().multiply(bestMatch.getMinPriceEur()).setScale(2, RoundingMode.HALF_UP)
                );
            }
            if (bestMatch.getMaxPriceEur() != null) {
                material.setTotalPriceMax(
                    material.getTotalQuantity().multiply(bestMatch.getMaxPriceEur()).setScale(2, RoundingMode.HALF_UP)
                );
            }
        } else {
            log.debug("No price found for material: {}", material.getMaterialName());
        }
    }
}
