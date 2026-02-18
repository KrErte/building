package com.buildquote.repository;

import com.buildquote.entity.BidAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BidAnalysisRepository extends JpaRepository<BidAnalysis, UUID> {

    List<BidAnalysis> findByBidId(UUID bidId);

    List<BidAnalysis> findByCampaignId(UUID campaignId);

    List<BidAnalysis> findByCampaignIdAndAnalysisType(UUID campaignId, BidAnalysis.AnalysisType analysisType);
}
