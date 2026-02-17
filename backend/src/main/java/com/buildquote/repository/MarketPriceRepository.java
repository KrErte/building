package com.buildquote.repository;

import com.buildquote.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketPriceRepository extends JpaRepository<MarketPrice, UUID> {

    @Query("SELECT m FROM MarketPrice m WHERE LOWER(m.category) = LOWER(:category)")
    Optional<MarketPrice> findByCategory(@Param("category") String category);

    @Query("SELECT m FROM MarketPrice m WHERE LOWER(m.category) = LOWER(:category) AND LOWER(m.region) = LOWER(:region)")
    Optional<MarketPrice> findByCategoryAndRegion(@Param("category") String category, @Param("region") String region);
}
