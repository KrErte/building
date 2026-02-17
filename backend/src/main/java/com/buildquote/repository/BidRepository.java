package com.buildquote.repository;

import com.buildquote.entity.Bid;
import com.buildquote.entity.RfqCampaign;
import com.buildquote.entity.RfqEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BidRepository extends JpaRepository<Bid, UUID> {
    List<Bid> findByCampaignOrderBySubmittedAtDesc(RfqCampaign campaign);
    Optional<Bid> findByRfqEmail(RfqEmail rfqEmail);
    List<Bid> findAllByOrderBySubmittedAtDesc();
    int countByCampaign(RfqCampaign campaign);
}
