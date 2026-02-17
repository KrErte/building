package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_material_bundles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkMaterialBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_category", nullable = false)
    private String workCategory;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(name = "ratio", nullable = false, precision = 10, scale = 4)
    private BigDecimal ratio;

    @Column(name = "unit_type", nullable = false)
    private String unitType; // "m2", "tk_per_m2", "jm_per_m2", etc.

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
