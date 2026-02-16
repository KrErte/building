package com.buildquote.service;

import com.buildquote.dto.CompanyDto;
import com.buildquote.dto.CompanyPageResponse;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private final SupplierRepository supplierRepository;
    private final SupplierSearchService supplierSearchService;
    private final JdbcTemplate jdbcTemplate;

    public CompanyService(SupplierRepository supplierRepository, SupplierSearchService supplierSearchService, JdbcTemplate jdbcTemplate) {
        this.supplierRepository = supplierRepository;
        this.supplierSearchService = supplierSearchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CompanyPageResponse getCompanies(int page, int size, String search, String sortBy, String sortDir) {
        // Use combined query from both suppliers_unified and crawler.company
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.toLowerCase() + "%" : null;
        int offset = page * size;

        String sql = buildCombinedQuery(searchPattern, sortBy, sortDir, size, offset);
        String countSql = buildCountQuery(searchPattern);

        List<CompanyDto> companies = new ArrayList<>();
        long totalElements = 0;

        try {
            // Get total count
            if (searchPattern != null) {
                totalElements = jdbcTemplate.queryForObject(countSql, Long.class, searchPattern, searchPattern);
            } else {
                totalElements = jdbcTemplate.queryForObject(countSql, Long.class);
            }

            // Get paginated results
            List<Map<String, Object>> rows;
            if (searchPattern != null) {
                rows = jdbcTemplate.queryForList(sql, searchPattern, searchPattern);
            } else {
                rows = jdbcTemplate.queryForList(sql);
            }

            for (Map<String, Object> row : rows) {
                companies.add(mapRowToDto(row));
            }
        } catch (Exception e) {
            // Fallback to suppliers_unified only
            System.err.println("CompanyService combined query failed: " + e.getMessage());
            e.printStackTrace();
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            String sortField = mapSortField(sortBy);
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
            Page<Supplier> supplierPage = supplierRepository.searchCompanies(search, pageable);

            companies = supplierPage.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            totalElements = supplierPage.getTotalElements();
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);

        return CompanyPageResponse.builder()
                .companies(companies)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    private String buildCombinedQuery(String searchPattern, String sortBy, String sortDir, int limit, int offset) {
        String orderBy = mapSortFieldSql(sortBy);
        String direction = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        String searchClause = searchPattern != null ?
            "WHERE LOWER(company_name) LIKE ? OR LOWER(city) LIKE ?" : "";

        return String.format("""
            SELECT id, company_name, email, phone, website, address, city, county, source, categories
            FROM (
                SELECT id::text, company_name, email, phone, website, address, city, county, source, categories
                FROM public.suppliers_unified
                UNION ALL
                SELECT id::text, legal_name as company_name,
                       COALESCE(email[1], '') as email,
                       COALESCE(phone[1], '') as phone,
                       website, address, city, county,
                       'BUSINESS_REGISTRY' as source, NULL as categories
                FROM crawler.company
            ) combined
            %s
            ORDER BY %s %s NULLS LAST
            LIMIT %d OFFSET %d
            """, searchClause, orderBy, direction, limit, offset);
    }

    private String buildCountQuery(String searchPattern) {
        String searchClause = searchPattern != null ?
            "WHERE LOWER(company_name) LIKE ? OR LOWER(city) LIKE ?" : "";

        return String.format("""
            SELECT COUNT(*) FROM (
                SELECT company_name, city FROM public.suppliers_unified
                UNION ALL
                SELECT legal_name as company_name, city FROM crawler.company
            ) combined
            %s
            """, searchClause);
    }

    private String mapSortFieldSql(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "company_name";
        }
        return switch (sortBy.toLowerCase()) {
            case "name" -> "company_name";
            case "city", "location" -> "city";
            case "source" -> "source";
            default -> "company_name";
        };
    }

    private CompanyDto mapRowToDto(Map<String, Object> row) {
        String categoriesStr = row.get("categories") != null ? row.get("categories").toString() : null;
        List<String> categories = new ArrayList<>();
        if (categoriesStr != null && !categoriesStr.isEmpty()) {
            // Handle PostgreSQL array format {val1,val2}
            categoriesStr = categoriesStr.replaceAll("[{}]", "");
            if (!categoriesStr.isEmpty()) {
                categories = Arrays.asList(categoriesStr.split(","));
            }
        }

        return CompanyDto.builder()
                .id(row.get("id") != null ? row.get("id").toString() : null)
                .companyName((String) row.get("company_name"))
                .email((String) row.get("email"))
                .phone((String) row.get("phone"))
                .website((String) row.get("website"))
                .address((String) row.get("address"))
                .city((String) row.get("city"))
                .county((String) row.get("county"))
                .source((String) row.get("source"))
                .categories(categories)
                .serviceAreas(List.of())
                .build();
    }

    private String mapSortField(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "companyName";
        }
        return switch (sortBy.toLowerCase()) {
            case "name" -> "companyName";
            case "city", "location" -> "city";
            case "source" -> "source";
            case "rating" -> "googleRating";
            default -> "companyName";
        };
    }

    public long getTotalCount() {
        // Include both suppliers_unified and crawler.company counts
        return supplierSearchService.getTotalSupplierCount();
    }

    private CompanyDto toDto(Supplier supplier) {
        return CompanyDto.builder()
                .id(supplier.getId() != null ? supplier.getId().toString() : null)
                .companyName(supplier.getCompanyName())
                .contactPerson(supplier.getContactPerson())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .website(supplier.getWebsite())
                .address(supplier.getAddress())
                .city(supplier.getCity())
                .county(supplier.getCounty())
                .categories(supplier.getCategories() != null ? Arrays.asList(supplier.getCategories()) : List.of())
                .serviceAreas(supplier.getServiceAreas() != null ? Arrays.asList(supplier.getServiceAreas()) : List.of())
                .source(supplier.getSource())
                .googleRating(supplier.getGoogleRating())
                .googleReviewCount(supplier.getGoogleReviewCount())
                .trustScore(supplier.getTrustScore())
                .isVerified(supplier.getIsVerified())
                .build();
    }
}
