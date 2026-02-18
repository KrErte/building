package com.buildquote.controller;

import com.buildquote.entity.ComponentRecipe;
import com.buildquote.repository.ComponentRecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/recipes")
@RequiredArgsConstructor
@Slf4j
public class RecipeController {

    private final ComponentRecipeRepository recipeRepository;

    @GetMapping
    public ResponseEntity<List<ComponentRecipe>> listAll() {
        return ResponseEntity.ok(recipeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ComponentRecipe> create(@RequestBody ComponentRecipe recipe) {
        log.info("Creating recipe: {} -> {}", recipe.getComponentName(), recipe.getMaterialName());
        ComponentRecipe saved = recipeRepository.save(recipe);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComponentRecipe> update(@PathVariable Long id, @RequestBody ComponentRecipe recipe) {
        return recipeRepository.findById(id)
            .map(existing -> {
                existing.setComponentName(recipe.getComponentName());
                existing.setComponentCategory(recipe.getComponentCategory());
                existing.setMaterialName(recipe.getMaterialName());
                existing.setQuantityPerUnit(recipe.getQuantityPerUnit());
                existing.setMaterialUnit(recipe.getMaterialUnit());
                existing.setNotes(recipe.getNotes());
                return ResponseEntity.ok(recipeRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (recipeRepository.existsById(id)) {
            recipeRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
