package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pipelines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pipeline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PipelineStatus status = PipelineStatus.CREATED;

    @Builder.Default
    private Integer currentStep = 0;

    @Builder.Default
    private Integer totalSteps = 0;

    @Column(columnDefinition = "TEXT")
    private String contextJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PipelineStep> steps = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
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

    public enum PipelineStatus {
        CREATED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    }
}
