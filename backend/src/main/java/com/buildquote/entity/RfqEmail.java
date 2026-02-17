package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rfq_emails")
@Data
public class RfqEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private RfqCampaign campaign;

    private UUID supplierId;
    private String supplierName;
    private String supplierEmail;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(nullable = false)
    private String status = "QUEUED"; // QUEUED, SENT, DELIVERED, BOUNCED, OPENED, RESPONDED

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime openedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime remindedAt;
    private Integer reminderCount = 0;
}
