package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "negotiation_rounds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationRound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id", nullable = false)
    private Bid bid;

    @Column(nullable = false)
    private Integer roundNumber;

    @Column(length = 500)
    private String ourSubject;

    @Column(columnDefinition = "TEXT")
    private String ourMessage;

    @Column(columnDefinition = "TEXT")
    private String theirReply;

    @Column(precision = 12, scale = 2)
    private BigDecimal proposedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoundStatus status = RoundStatus.DRAFT;

    private LocalDateTime sentAt;
    private LocalDateTime repliedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum RoundStatus {
        DRAFT, SENT, REPLIED, FAILED
    }
}
