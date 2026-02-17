package com.buildquote.service;

import com.buildquote.dto.*;
import com.buildquote.entity.*;
import com.buildquote.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RfqService {
    private static final Logger log = LoggerFactory.getLogger(RfqService.class);

    private final RfqCampaignRepository campaignRepository;
    private final RfqEmailRepository emailRepository;
    private final BidRepository bidRepository;
    private final SupplierRepository supplierRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final EmailService emailService;

    public RfqService(RfqCampaignRepository campaignRepository,
                      RfqEmailRepository emailRepository,
                      BidRepository bidRepository,
                      SupplierRepository supplierRepository,
                      MarketPriceRepository marketPriceRepository,
                      EmailService emailService) {
        this.campaignRepository = campaignRepository;
        this.emailRepository = emailRepository;
        this.bidRepository = bidRepository;
        this.supplierRepository = supplierRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.emailService = emailService;
    }

    @Transactional
    public CampaignDto createAndSendCampaign(RfqRequest request) {
        // Create campaign
        RfqCampaign campaign = new RfqCampaign();
        campaign.setTitle(request.getTitle());
        campaign.setCategory(request.getCategory());
        campaign.setLocation(request.getLocation());
        campaign.setQuantity(request.getQuantity());
        campaign.setUnit(request.getUnit());
        campaign.setSpecifications(request.getSpecifications());
        campaign.setMaxBudget(request.getMaxBudget());
        if (request.getDeadline() != null && !request.getDeadline().isEmpty()) {
            campaign.setDeadline(LocalDate.parse(request.getDeadline()));
        }
        campaign.setStatus("SENDING");
        campaign.setCreatedAt(LocalDateTime.now());
        campaign = campaignRepository.save(campaign);

        // Get suppliers to send to
        List<Supplier> suppliers;
        if (request.getSupplierIds() != null && !request.getSupplierIds().isEmpty()) {
            suppliers = request.getSupplierIds().stream()
                    .map(UUID::fromString)
                    .map(supplierRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } else {
            // If no specific suppliers, get all matching the category
            suppliers = supplierRepository.findByCategory(campaign.getCategory(), 50);
        }

        // Create RFQ emails and send
        int sentCount = 0;
        for (Supplier supplier : suppliers) {
            if (supplier.getEmail() == null || supplier.getEmail().isEmpty()) {
                continue;
            }

            RfqEmail rfqEmail = new RfqEmail();
            rfqEmail.setCampaign(campaign);
            rfqEmail.setSupplierId(supplier.getId());
            rfqEmail.setSupplierName(supplier.getCompanyName());
            rfqEmail.setSupplierEmail(supplier.getEmail());
            rfqEmail.setToken(generateToken());
            rfqEmail.setStatus("QUEUED");
            rfqEmail = emailRepository.save(rfqEmail);

            // Send the email
            boolean sent = emailService.sendRfqEmail(
                    supplier.getEmail(),
                    supplier.getCompanyName(),
                    campaign.getTitle(),
                    campaign.getCategory(),
                    campaign.getLocation(),
                    campaign.getQuantity() != null ? campaign.getQuantity().toString() : "",
                    campaign.getUnit(),
                    campaign.getSpecifications(),
                    rfqEmail.getToken()
            );

            if (sent) {
                rfqEmail.setStatus("SENT");
                rfqEmail.setSentAt(LocalDateTime.now());
                emailRepository.save(rfqEmail);
                sentCount++;
            }
        }

        // Update campaign stats
        campaign.setTotalSent(sentCount);
        campaign.setStatus("ACTIVE");
        campaign = campaignRepository.save(campaign);

        log.info("Campaign {} created and sent to {} suppliers", campaign.getId(), sentCount);

        return toCampaignDto(campaign);
    }

    public BidPageDto getBidPage(String token) {
        RfqEmail rfqEmail = emailRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid bid token"));

        // Mark as opened
        if (rfqEmail.getOpenedAt() == null) {
            rfqEmail.setOpenedAt(LocalDateTime.now());
            rfqEmail.setStatus("OPENED");
            emailRepository.save(rfqEmail);

            // Update campaign stats
            RfqCampaign campaign = rfqEmail.getCampaign();
            campaign.setTotalOpened(campaign.getTotalOpened() + 1);
            campaignRepository.save(campaign);
        }

        RfqCampaign campaign = rfqEmail.getCampaign();

        // Check if already submitted
        Optional<Bid> existingBid = bidRepository.findByRfqEmail(rfqEmail);

        BidPageDto dto = BidPageDto.builder()
                .token(token)
                .campaignTitle(campaign.getTitle())
                .category(campaign.getCategory())
                .location(campaign.getLocation())
                .quantity(campaign.getQuantity())
                .unit(campaign.getUnit())
                .specifications(campaign.getSpecifications())
                .deadline(campaign.getDeadline() != null ? campaign.getDeadline().toString() : null)
                .maxBudget(campaign.getMaxBudget())
                .supplierName(rfqEmail.getSupplierName())
                .supplierEmail(rfqEmail.getSupplierEmail())
                .alreadySubmitted(existingBid.isPresent())
                .build();

        if (existingBid.isPresent()) {
            dto.setExistingBidPrice(existingBid.get().getPrice().toString());
            dto.setExistingBidNotes(existingBid.get().getNotes());
        }

        return dto;
    }

    @Transactional
    public BidDto submitBid(String token, BidSubmissionRequest request) {
        RfqEmail rfqEmail = emailRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid bid token"));

        // Check if already submitted
        if (bidRepository.findByRfqEmail(rfqEmail).isPresent()) {
            throw new RuntimeException("Bid already submitted for this RFQ");
        }

        RfqCampaign campaign = rfqEmail.getCampaign();

        Bid bid = new Bid();
        bid.setRfqEmail(rfqEmail);
        bid.setCampaign(campaign);
        bid.setSupplierName(rfqEmail.getSupplierName());
        bid.setSupplierEmail(rfqEmail.getSupplierEmail());
        bid.setPrice(request.getPrice());
        bid.setTimelineDays(request.getTimelineDays());
        if (request.getDeliveryDate() != null && !request.getDeliveryDate().isEmpty()) {
            bid.setDeliveryDate(LocalDate.parse(request.getDeliveryDate()));
        }
        bid.setNotes(request.getNotes());
        bid.setLineItems(request.getLineItems());
        bid.setStatus("RECEIVED");
        bid.setSubmittedAt(LocalDateTime.now());
        bid = bidRepository.save(bid);

        // Update RFQ email status
        rfqEmail.setStatus("RESPONDED");
        rfqEmail.setRespondedAt(LocalDateTime.now());
        emailRepository.save(rfqEmail);

        // Update campaign stats
        campaign.setTotalResponded(campaign.getTotalResponded() + 1);
        campaignRepository.save(campaign);

        log.info("Bid {} submitted for campaign {} by {}", bid.getId(), campaign.getId(), rfqEmail.getSupplierName());

        // Send notification email to project owner
        sendBidNotificationEmail(bid, campaign);

        return toBidDto(bid);
    }

    private void sendBidNotificationEmail(Bid bid, RfqCampaign campaign) {
        // For now, send to a fixed notification address (could be project owner in future)
        String subject = "Uus pakkumine: " + campaign.getTitle();
        String htmlBody = buildBidNotificationHtml(bid, campaign);

        // Send notification (uses dev mode settings)
        emailService.sendEmail("kristo.erte@gmail.com", subject, htmlBody);
    }

    private String buildBidNotificationHtml(Bid bid, RfqCampaign campaign) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset='UTF-8'></head>");
        html.append("<body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>");

        html.append("<div style='background: #10b981; color: white; padding: 20px; border-radius: 8px 8px 0 0;'>");
        html.append("<h1 style='margin: 0; font-size: 24px;'>Uus pakkumine saabunud!</h1>");
        html.append("</div>");

        html.append("<div style='background: #f9fafb; padding: 24px; border: 1px solid #e5e7eb;'>");
        html.append("<h2 style='margin: 0 0 16px 0;'>").append(campaign.getTitle()).append("</h2>");

        html.append("<table style='width: 100%;'>");
        html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Tarnija:</td><td><strong>")
            .append(bid.getSupplierName()).append("</strong></td></tr>");
        html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Hind:</td><td><strong style='color: #059669; font-size: 20px;'>€")
            .append(bid.getPrice()).append("</strong></td></tr>");
        if (bid.getTimelineDays() != null) {
            html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Tähtaeg:</td><td><strong>")
                .append(bid.getTimelineDays()).append(" päeva</strong></td></tr>");
        }
        if (bid.getNotes() != null && !bid.getNotes().isEmpty()) {
            html.append("<tr><td style='padding: 8px 0; color: #6b7280;'>Märkused:</td><td>")
                .append(bid.getNotes()).append("</td></tr>");
        }
        html.append("</table>");

        html.append("</div>");
        html.append("</body></html>");

        return html.toString();
    }

    public List<CampaignDto> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toCampaignDto)
                .collect(Collectors.toList());
    }

    public CampaignDto getCampaign(String id) {
        RfqCampaign campaign = campaignRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        CampaignDto dto = toCampaignDto(campaign);

        // Include bids
        List<BidDto> bids = bidRepository.findByCampaignOrderBySubmittedAtDesc(campaign).stream()
                .map(this::toBidDto)
                .collect(Collectors.toList());
        dto.setBids(bids);

        return dto;
    }

    public List<BidDto> getAllBids() {
        return bidRepository.findAllByOrderBySubmittedAtDesc().stream()
                .map(this::toBidDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all bids for a campaign with AI analysis
     */
    public List<BidDto> getCampaignBidsWithAnalysis(String campaignId) {
        RfqCampaign campaign = campaignRepository.findById(UUID.fromString(campaignId))
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        List<Bid> bids = bidRepository.findByCampaignOrderBySubmittedAtDesc(campaign);

        // Get market price for comparison
        Optional<MarketPrice> marketPriceOpt = Optional.empty();
        if (campaign.getCategory() != null) {
            marketPriceOpt = marketPriceRepository.findByCategoryAndRegion(
                campaign.getCategory(), campaign.getLocation()
            );
            if (marketPriceOpt.isEmpty()) {
                marketPriceOpt = marketPriceRepository.findByCategory(campaign.getCategory());
            }
        }

        BigDecimal medianPrice = marketPriceOpt.map(MarketPrice::getMedianPrice)
                .orElse(new BigDecimal("30")); // Default fallback

        return bids.stream()
                .map(bid -> toBidDtoWithAnalysis(bid, campaign, medianPrice))
                .collect(Collectors.toList());
    }

    private BidDto toBidDtoWithAnalysis(Bid bid, RfqCampaign campaign, BigDecimal marketMedianPerUnit) {
        BidDto dto = toBidDto(bid);

        // Calculate price per unit
        BigDecimal quantity = campaign.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            quantity = BigDecimal.ONE;
        }

        BigDecimal bidPricePerUnit = bid.getPrice().divide(quantity, 2, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = marketMedianPerUnit.multiply(quantity);

        // Calculate percent from median
        if (expectedTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentDiff = bid.getPrice().subtract(expectedTotal)
                    .divide(expectedTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            dto.setPercentFromMedian(percentDiff.setScale(1, RoundingMode.HALF_UP));

            // Determine verdict
            double pctDiff = percentDiff.doubleValue();
            if (pctDiff <= -15) {
                dto.setVerdict("GREAT_DEAL");
            } else if (pctDiff <= 10) {
                dto.setVerdict("FAIR");
            } else if (pctDiff <= 40) {
                dto.setVerdict("OVERPRICED");
            } else {
                dto.setVerdict("RED_FLAG");
            }
        } else {
            dto.setPercentFromMedian(BigDecimal.ZERO);
            dto.setVerdict("FAIR");
        }

        dto.setMarketPricePerUnit(marketMedianPerUnit);

        return dto;
    }

    private CampaignDto toCampaignDto(RfqCampaign campaign) {
        return CampaignDto.builder()
                .id(campaign.getId().toString())
                .title(campaign.getTitle())
                .category(campaign.getCategory())
                .location(campaign.getLocation())
                .quantity(campaign.getQuantity())
                .unit(campaign.getUnit())
                .specifications(campaign.getSpecifications())
                .deadline(campaign.getDeadline() != null ? campaign.getDeadline().toString() : null)
                .maxBudget(campaign.getMaxBudget())
                .status(campaign.getStatus())
                .totalSent(campaign.getTotalSent())
                .totalResponded(campaign.getTotalResponded())
                .createdAt(campaign.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private BidDto toBidDto(Bid bid) {
        return BidDto.builder()
                .id(bid.getId().toString())
                .supplierName(bid.getSupplierName())
                .supplierEmail(bid.getSupplierEmail())
                .price(bid.getPrice())
                .currency(bid.getCurrency())
                .timelineDays(bid.getTimelineDays())
                .deliveryDate(bid.getDeliveryDate() != null ? bid.getDeliveryDate().toString() : null)
                .notes(bid.getNotes())
                .status(bid.getStatus())
                .submittedAt(bid.getSubmittedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
