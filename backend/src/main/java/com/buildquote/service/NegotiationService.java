package com.buildquote.service;

import com.buildquote.dto.NegotiationRequest;
import com.buildquote.dto.NegotiationRoundDto;
import com.buildquote.entity.Bid;
import com.buildquote.entity.NegotiationRound;
import com.buildquote.repository.BidRepository;
import com.buildquote.repository.NegotiationRoundRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NegotiationService {

    private static final int MAX_ROUNDS = 5;

    private final BidRepository bidRepository;
    private final NegotiationRoundRepository roundRepository;
    private final AnthropicService anthropicService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NegotiationRoundDto sendNegotiation(UUID bidId, NegotiationRequest request) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        int existingRounds = roundRepository.countByBid(bid);
        if (existingRounds >= MAX_ROUNDS) {
            throw new RuntimeException("Maximum negotiation rounds (" + MAX_ROUNDS + ") reached for this bid");
        }

        int roundNumber = existingRounds + 1;

        // Get previous rounds for context
        List<NegotiationRound> previousRounds = roundRepository.findByBidOrderByRoundNumberAsc(bid);

        // Generate AI email
        String tone = request.getTone() != null ? request.getTone() : "FRIENDLY";
        String aiEmail = generateNegotiationEmail(bid, request, previousRounds, roundNumber, tone);

        // Parse subject and body from AI response
        String subject;
        String body;
        try {
            String json = extractJson(aiEmail);
            JsonNode root = objectMapper.readTree(json);
            subject = root.path("subject").asText("Negotiation - " + bid.getCampaign().getTitle());
            body = root.path("body").asText(aiEmail);
        } catch (Exception e) {
            subject = "Negotiation Round " + roundNumber + " - " + bid.getCampaign().getTitle();
            body = aiEmail;
        }

        // If user provided a custom message, use it as the body
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            body = request.getMessage();
        }

        // Create round
        NegotiationRound round = NegotiationRound.builder()
                .bid(bid)
                .roundNumber(roundNumber)
                .ourSubject(subject)
                .ourMessage(body)
                .proposedPrice(request.getTargetPrice())
                .status(NegotiationRound.RoundStatus.DRAFT)
                .build();

        // Send email
        boolean sent = false;
        if (bid.getSupplierEmail() != null && !bid.getSupplierEmail().isBlank()) {
            sent = emailService.sendEmail(bid.getSupplierEmail(), subject, wrapHtml(body));
        }

        if (sent) {
            round.setStatus(NegotiationRound.RoundStatus.SENT);
            round.setSentAt(LocalDateTime.now());
            log.info("Negotiation email sent to {} for bid {} (round {})",
                    bid.getSupplierEmail(), bidId, roundNumber);
        } else {
            round.setStatus(NegotiationRound.RoundStatus.FAILED);
            log.warn("Failed to send negotiation email for bid {} (round {})", bidId, roundNumber);
        }

        round = roundRepository.save(round);

        // Update bid status
        bid.setStatus("COUNTER_OFFERED");
        bidRepository.save(bid);

        return toDto(round);
    }

    @Transactional(readOnly = true)
    public List<NegotiationRoundDto> listRounds(UUID bidId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        return roundRepository.findByBidOrderByRoundNumberAsc(bid).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public NegotiationRoundDto recordReply(UUID roundId, String replyBody) {
        NegotiationRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Negotiation round not found"));

        round.setTheirReply(replyBody);
        round.setStatus(NegotiationRound.RoundStatus.REPLIED);
        round.setRepliedAt(LocalDateTime.now());
        round = roundRepository.save(round);

        return toDto(round);
    }

    private String generateNegotiationEmail(Bid bid, NegotiationRequest request,
                                             List<NegotiationRound> previousRounds,
                                             int roundNumber, String tone) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("""
                Generate a professional negotiation email for a construction bid.
                Supplier: %s
                Current bid price: EUR %s
                Our target price: EUR %s
                Category: %s
                Round number: %d of max %d
                Tone: %s
                """,
                bid.getSupplierName(),
                bid.getPrice(),
                request.getTargetPrice(),
                bid.getCampaign() != null ? bid.getCampaign().getCategory() : "General",
                roundNumber, MAX_ROUNDS, tone));

        if (!previousRounds.isEmpty()) {
            prompt.append("\nPrevious negotiation history:\n");
            for (NegotiationRound prev : previousRounds) {
                prompt.append(String.format("  Round %d: We proposed EUR %s. ",
                        prev.getRoundNumber(), prev.getProposedPrice()));
                if (prev.getTheirReply() != null) {
                    prompt.append("Their reply: ").append(prev.getTheirReply());
                }
                prompt.append("\n");
            }
        }

        prompt.append("""

                Return as JSON:
                {
                  "subject": "email subject line",
                  "body": "plain text email body"
                }
                """);

        return anthropicService.callClaude(prompt.toString());
    }

    private String wrapHtml(String text) {
        return "<div style=\"font-family: Arial, sans-serif; line-height: 1.6;\">"
                + text.replace("\n", "<br/>")
                + "</div>";
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private NegotiationRoundDto toDto(NegotiationRound round) {
        return NegotiationRoundDto.builder()
                .id(round.getId())
                .bidId(round.getBid().getId())
                .roundNumber(round.getRoundNumber())
                .ourSubject(round.getOurSubject())
                .ourMessage(round.getOurMessage())
                .theirReply(round.getTheirReply())
                .proposedPrice(round.getProposedPrice())
                .status(round.getStatus().name())
                .sentAt(round.getSentAt())
                .repliedAt(round.getRepliedAt())
                .createdAt(round.getCreatedAt())
                .build();
    }
}
