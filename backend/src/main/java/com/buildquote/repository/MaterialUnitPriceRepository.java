package com.buildquote.repository;

import com.buildquote.entity.MaterialUnitPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialUnitPriceRepository extends JpaRepository<MaterialUnitPrice, Long> {

    List<MaterialUnitPrice> findByCategory(String category);

    List<MaterialUnitPrice> findByCategoryIgnoreCase(String category);

    @Query("SELECT m FROM MaterialUnitPrice m WHERE LOWER(m.category) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(m.materialName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<MaterialUnitPrice> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT m.category FROM MaterialUnitPrice m ORDER BY m.category")
    List<String> findAllCategories();

    long count();
}
