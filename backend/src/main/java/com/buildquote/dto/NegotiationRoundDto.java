package com.buildquote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationRoundDto {
    private UUID id;
    private UUID bidId;
    private Integer roundNumber;
    private String ourSubject;
    private String ourMessage;
    private String theirReply;
    private BigDecimal proposedPrice;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
}
