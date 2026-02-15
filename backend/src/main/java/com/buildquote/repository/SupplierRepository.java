package com.buildquote.repository;

import com.buildquote.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByGooglePlaceId(String googlePlaceId);

    Optional<Supplier> findByOnboardingToken(String onboardingToken);

    boolean existsByGooglePlaceId(String googlePlaceId);

    @Query(value = "SELECT COUNT(*) FROM suppliers_unified WHERE categories LIKE CONCAT('%', :category, '%')", nativeQuery = true)
    int countByCategory(@Param("category") String category);

    @Query(value = "SELECT COUNT(*) FROM suppliers_unified WHERE categories LIKE CONCAT('%', :category, '%') AND service_areas LIKE CONCAT('%', :city, '%')", nativeQuery = true)
    int countByCategoryAndCity(@Param("category") String category, @Param("city") String city);

    @Query(value = "SELECT * FROM suppliers_unified WHERE categories LIKE CONCAT('%', :category, '%') LIMIT :limit", nativeQuery = true)
    List<Supplier> findByCategory(@Param("category") String category, @Param("limit") int limit);

    @Query("SELECT s FROM Supplier s WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(s.companyName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Supplier> searchCompanies(@Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Supplier s")
    long countAll();

    /**
     * Batch count suppliers by multiple categories - much faster than individual queries.
     * Returns list of [category, count] arrays.
     */
    @Query(value = "SELECT categories, COUNT(*) as cnt FROM suppliers_unified " +
                   "WHERE categories IS NOT NULL GROUP BY categories", nativeQuery = true)
    List<Object[]> countAllByCategory();

    /**
     * Get total count per city for quick lookups.
     */
    @Query(value = "SELECT city, COUNT(*) as cnt FROM suppliers_unified " +
                   "WHERE city IS NOT NULL GROUP BY city", nativeQuery = true)
    List<Object[]> countAllByCity();
}
