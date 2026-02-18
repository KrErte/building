package com.buildquote.repository;

import com.buildquote.entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime after);

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, LocalDateTime after);

    @Query("SELECT AVG(a.responseTimeMs) FROM ApiUsageLog a WHERE a.createdAt > :after")
    Double averageResponseTimeSince(@Param("after") LocalDateTime after);
}
