package com.buildquote.repository;

import com.buildquote.entity.BidAnalysis;
import com.buildquote.entity.NegotiationTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NegotiationTargetRepository extends JpaRepository<NegotiationTarget, UUID> {

    List<NegotiationTarget> findByBidAnalysis(BidAnalysis bidAnalysis);

    List<NegotiationTarget> findByBidId(UUID bidId);
}
