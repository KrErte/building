package com.buildquote.service;

import com.buildquote.dto.CompanyDto;
import com.buildquote.dto.CompanyPageResponse;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private final SupplierRepository supplierRepository;
    private final SupplierSearchService supplierSearchService;

    public CompanyService(SupplierRepository supplierRepository, SupplierSearchService supplierSearchService) {
        this.supplierRepository = supplierRepository;
        this.supplierSearchService = supplierSearchService;
    }

    public CompanyPageResponse getCompanies(int page, int size, String search, String sortBy, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = mapSortField(sortBy);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        Page<Supplier> supplierPage = supplierRepository.searchCompanies(search, pageable);

        List<CompanyDto> companies = supplierPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return CompanyPageResponse.builder()
                .companies(companies)
                .page(page)
                .size(size)
                .totalElements(supplierPage.getTotalElements())
                .totalPages(supplierPage.getTotalPages())
                .hasNext(supplierPage.hasNext())
                .hasPrevious(supplierPage.hasPrevious())
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
