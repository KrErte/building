package com.buildquote.repository;

import com.buildquote.entity.CompanyEnrichment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyEnrichmentRepository extends JpaRepository<CompanyEnrichment, UUID> {

    Optional<CompanyEnrichment> findBySupplierId(UUID supplierId);

    List<CompanyEnrichment> findByCacheExpiresAtBefore(LocalDateTime now);
}
