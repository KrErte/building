package com.buildquote.service;

import com.buildquote.entity.Bid;
import com.buildquote.entity.MarketPrice;
import com.buildquote.entity.RfqCampaign;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.MarketPriceRepository;
import com.buildquote.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketPriceLearningService {

    private final MarketPriceRepository marketPriceRepository;
    private final SupplierRepository supplierRepository;

    // Recency weight: new data counts 2x vs existing average
    private static final BigDecimal RECENCY_WEIGHT = new BigDecimal("2.0");

    @Transactional
    public void onBidReceived(Bid bid) {
        RfqCampaign campaign = bid.getCampaign();
        if (campaign == null || campaign.getCategory() == null) {
            return;
        }

        // Update market prices
        updateMarketPrice(campaign, bid);

        // Update supplier response tracking
        updateSupplierResponseTracking(bid);
    }

    private void updateMarketPrice(RfqCampaign campaign, Bid bid) {
        String category = campaign.getCategory();
        String unit = campaign.getUnit();
        BigDecimal quantity = campaign.getQuantity();

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = BigDecimal.ONE;
        }

        // Calculate unit price from bid
        BigDecimal unitPrice = bid.getPrice().divide(quantity, 4, RoundingMode.HALF_UP);

        // Find or create market price entry
        Optional<MarketPrice> existing = marketPriceRepository.findByCategory(category);

        if (existing.isPresent()) {
            MarketPrice mp = existing.get();
            int oldCount = mp.getSampleCount() != null ? mp.getSampleCount() : 0;

            // Weighted moving average: new data gets 2x weight
            // newAvg = (oldAvg * oldCount + newValue * RECENCY_WEIGHT) / (oldCount + RECENCY_WEIGHT)
            BigDecimal oldAvg = mp.getAvgPrice() != null ? mp.getAvgPrice() : unitPrice;
            BigDecimal weightedOld = oldAvg.multiply(BigDecimal.valueOf(oldCount));
            BigDecimal weightedNew = unitPrice.multiply(RECENCY_WEIGHT);
            BigDecimal totalWeight = BigDecimal.valueOf(oldCount).add(RECENCY_WEIGHT);
            BigDecimal newAvg = weightedOld.add(weightedNew).divide(totalWeight, 4, RoundingMode.HALF_UP);

            mp.setAvgPrice(newAvg);
            mp.setMedianPrice(newAvg); // Approximate: use avg as median proxy

            // Update min/max
            if (mp.getMinPrice() == null || unitPrice.compareTo(mp.getMinPrice()) < 0) {
                mp.setMinPrice(unitPrice);
            }
            if (mp.getMaxPrice() == null || unitPrice.compareTo(mp.getMaxPrice()) > 0) {
                mp.setMaxPrice(unitPrice);
            }

            mp.setSampleCount(oldCount + 1);
            mp.setLastUpdated(LocalDateTime.now());

            marketPriceRepository.save(mp);
            log.info("Updated market price for {}: avg={}, samples={}", category, newAvg, oldCount + 1);
        } else {
            // Create new entry
            MarketPrice mp = new MarketPrice();
            mp.setCategory(category);
            mp.setUnit(unit != null ? unit : "tk");
            mp.setMinPrice(unitPrice);
            mp.setMaxPrice(unitPrice);
            mp.setMedianPrice(unitPrice);
            mp.setAvgPrice(unitPrice);
            mp.setSampleCount(1);
            mp.setSource("BID_LEARNING");
            mp.setLastUpdated(LocalDateTime.now());

            marketPriceRepository.save(mp);
            log.info("Created market price entry for {}: price={}", category, unitPrice);
        }
    }

    private void updateSupplierResponseTracking(Bid bid) {
        if (bid.getRfqEmail() == null || bid.getRfqEmail().getSupplierId() == null) {
            return;
        }

        UUID supplierId = bid.getRfqEmail().getSupplierId();
        Optional<Supplier> supplierOpt = supplierRepository.findById(supplierId);
        if (supplierOpt.isEmpty()) return;

        Supplier supplier = supplierOpt.get();

        // Increment bids received
        int totalBids = (supplier.getTotalBidsReceived() != null ? supplier.getTotalBidsReceived() : 0) + 1;
        supplier.setTotalBidsReceived(totalBids);

        // Calculate response time
        if (bid.getRfqEmail().getSentAt() != null && bid.getSubmittedAt() != null) {
            long hours = Duration.between(bid.getRfqEmail().getSentAt(), bid.getSubmittedAt()).toHours();
            BigDecimal responseHours = BigDecimal.valueOf(hours);

            if (supplier.getAvgResponseTimeHours() != null && totalBids > 1) {
                // Running average
                BigDecimal oldAvg = supplier.getAvgResponseTimeHours();
                BigDecimal newAvg = oldAvg.multiply(BigDecimal.valueOf(totalBids - 1))
                        .add(responseHours)
                        .divide(BigDecimal.valueOf(totalBids), 1, RoundingMode.HALF_UP);
                supplier.setAvgResponseTimeHours(newAvg);
            } else {
                supplier.setAvgResponseTimeHours(responseHours);
            }
        }

        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);
        log.debug("Updated response tracking for supplier {}: totalBids={}", supplierId, totalBids);
    }
}
