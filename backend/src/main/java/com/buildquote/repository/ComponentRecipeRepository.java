package com.buildquote.repository;

import com.buildquote.entity.ComponentRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentRecipeRepository extends JpaRepository<ComponentRecipe, Long> {

    List<ComponentRecipe> findByComponentCategory(String category);

    List<ComponentRecipe> findByComponentNameIgnoreCase(String name);

    List<ComponentRecipe> findByComponentCategoryIgnoreCase(String category);
}
