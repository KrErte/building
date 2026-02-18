package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_reminders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_email_id", nullable = false)
    private RfqEmail rfqEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private RfqCampaign campaign;

    @Builder.Default
    private Integer reminderNumber = 1;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
