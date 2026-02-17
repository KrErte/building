package com.buildquote.controller;

import com.buildquote.dto.CompanyPageResponse;
import com.buildquote.service.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
@CrossOrigin
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<CompanyPageResponse> getCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city
    ) {
        CompanyPageResponse response = companyService.getCompanies(page, size, search, sort, dir, category, city);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    public ResponseEntity<java.util.Map<String, Long>> getCompanyCount() {
        long count = companyService.getTotalCount();
        return ResponseEntity.ok(java.util.Map.of("count", count));
    }
}
