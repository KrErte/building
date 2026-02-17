package com.buildquote.repository;

import com.buildquote.entity.PriceSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriceSourceRepository extends JpaRepository<PriceSource, Long> {

    Optional<PriceSource> findBySourceId(String sourceId);

    long count();
}
