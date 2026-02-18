package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String title;

    private String location;

    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String deadline;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectStage> stages = new ArrayList<>();

    private BigDecimal totalEstimateMin;
    private BigDecimal totalEstimateMax;
    private Integer totalSupplierCount;

    // Phase 1: Validation fields
    @Column(precision = 5, scale = 2)
    private BigDecimal parseConfidence;
    private String validationStatus;

    // Phase 4: Phased procurement fields
    @Builder.Default
    private Integer quotingHorizonDays = 90;
    private java.time.LocalDate constructionStartDate;

    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public enum ProjectStatus {
        DRAFT, PARSED, QUOTING, COMPLETED, ARCHIVED
    }
}
