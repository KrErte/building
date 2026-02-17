package com.buildquote.repository;

import com.buildquote.entity.WorkUnitPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkUnitPriceRepository extends JpaRepository<WorkUnitPrice, Long> {

    List<WorkUnitPrice> findByCategory(String category);

    List<WorkUnitPrice> findByCategoryIgnoreCase(String category);

    @Query("SELECT w FROM WorkUnitPrice w WHERE LOWER(w.category) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(w.materialName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<WorkUnitPrice> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT w.category FROM WorkUnitPrice w ORDER BY w.category")
    List<String> findAllCategories();

    long count();
}
