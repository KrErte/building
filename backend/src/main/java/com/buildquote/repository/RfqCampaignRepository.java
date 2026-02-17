package com.buildquote.repository;

import com.buildquote.entity.RfqCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RfqCampaignRepository extends JpaRepository<RfqCampaign, UUID> {
    List<RfqCampaign> findByStatusOrderByCreatedAtDesc(String status);
    List<RfqCampaign> findAllByOrderByCreatedAtDesc();
}
