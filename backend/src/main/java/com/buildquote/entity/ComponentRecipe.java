package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "component_recipes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComponentRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(name = "component_category")
    private String componentCategory;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(name = "quantity_per_unit", nullable = false, precision = 10, scale = 4)
    private BigDecimal quantityPerUnit;

    @Column(name = "material_unit", nullable = false)
    private String materialUnit;

    private String notes;

    @Column(name = "pipe_color")
    private String pipeColor; // PURPLE, BROWN, BLUE - only for pipe materials

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
