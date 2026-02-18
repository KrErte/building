package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bids")
@Data
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "rfq_email_id")
    private RfqEmail rfqEmail;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private RfqCampaign campaign;

    private String supplierName;
    private String supplierEmail;

    @Column(nullable = false)
    private BigDecimal price;

    private String currency = "EUR";
    private Integer timelineDays;
    private LocalDate deliveryDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String lineItems; // JSON string

    @Column(nullable = false)
    private String status = "RECEIVED"; // RECEIVED, UNDER_REVIEW, ACCEPTED, DECLINED, COUNTER_OFFERED

    @Column(length = 20)
    private String source = "FORM"; // FORM or EMAIL

    private LocalDateTime submittedAt = LocalDateTime.now();
}
