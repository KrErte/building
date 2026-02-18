package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_usage_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private UUID organizationId;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String method;

    private Integer statusCode;
    private Long responseTimeMs;
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
