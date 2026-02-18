package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_emails")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String messageId;

    @Column(nullable = false)
    private String fromEmail;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Builder.Default
    private boolean processed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_campaign_id")
    private RfqCampaign matchedCampaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_rfq_email_id")
    private RfqEmail matchedRfqEmail;

    private String matchStrategy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parsed_bid_id")
    private Bid parsedBid;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    private LocalDateTime processedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
