package com.buildquote.repository;

import com.buildquote.entity.RfqEmail;
import com.buildquote.entity.RfqCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RfqEmailRepository extends JpaRepository<RfqEmail, UUID> {
    Optional<RfqEmail> findByToken(String token);
    List<RfqEmail> findByCampaign(RfqCampaign campaign);
    List<RfqEmail> findByCampaignAndStatus(RfqCampaign campaign, String status);
    int countByCampaign(RfqCampaign campaign);
}
