package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rfq_campaigns")
@Data
public class RfqCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String specifications;

    private String category;
    private String location;
    private BigDecimal quantity;
    private String unit;

    private LocalDate deadline;
    private BigDecimal maxBudget;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT, SENDING, ACTIVE, CLOSED, AWARDED

    private Integer totalSent = 0;
    private Integer totalDelivered = 0;
    private Integer totalOpened = 0;
    private Integer totalResponded = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime closedAt;
}
