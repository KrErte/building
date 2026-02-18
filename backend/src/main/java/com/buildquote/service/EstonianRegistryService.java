package com.buildquote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Queries Estonian public data sources by supplier registry code.
 * Implements graceful degradation: if any query fails, enrich with what we have.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EstonianRegistryService {

    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class RegistryData {
        // Ariregister
        private String foundingDate;
        private Integer employeeCount;
        private BigDecimal annualRevenue;
        private Boolean isActive;

        // Maksu- ja Tolliamet
        private Boolean taxDebt;
        private BigDecimal taxDebtAmount;

        // Riigihangete Register
        private Integer publicProcurementCount;

        // Computed
        private Integer yearsInBusiness;
        private String financialTrend; // UP, DOWN, STABLE
        private String rawJson;
    }

    /**
     * Query all available Estonian public registries for the given registry code.
     * Graceful degradation: if any single query fails, continue with partial data.
     */
    public RegistryData queryRegistries(String registryCode) {
        if (registryCode == null || registryCode.isBlank()) {
            return RegistryData.builder().build();
        }

        Map<String, Object> allData = new HashMap<>();
        RegistryData.RegistryDataBuilder builder = RegistryData.builder();

        // 1. Ariregister (Estonian Business Registry) - public JSON API
        try {
            queryAriregister(registryCode, builder, allData);
        } catch (Exception e) {
            log.debug("Ariregister query failed for {}: {}", registryCode, e.getMessage());
        }

        // 2. Tax debt check (Maksu- ja Tolliamet)
        try {
            queryTaxDebt(registryCode, builder, allData);
        } catch (Exception e) {
            log.debug("Tax debt query failed for {}: {}", registryCode, e.getMessage());
        }

        // 3. Public procurement registry (Riigihangete Register)
        try {
            queryProcurementHistory(registryCode, builder, allData);
        } catch (Exception e) {
            log.debug("Procurement registry query failed for {}: {}", registryCode, e.getMessage());
        }

        // Store raw data
        try {
            builder.rawJson(objectMapper.writeValueAsString(allData));
        } catch (Exception e) {
            builder.rawJson("{}");
        }

        return builder.build();
    }

    private void queryAriregister(String registryCode, RegistryData.RegistryDataBuilder builder,
                                   Map<String, Object> allData) {
        // Estonian Business Registry API (ariregister.rik.ee)
        // This is a public API that returns basic company info
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://ariregister.rik.ee/est/api/autocomplete?q=" + registryCode;

        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode results = root.path("results");
                if (results.isArray() && results.size() > 0) {
                    JsonNode company = results.get(0);

                    String regDate = company.path("registered_date").asText(null);
                    if (regDate != null && !regDate.isEmpty()) {
                        builder.foundingDate(regDate);
                        try {
                            LocalDate founded = LocalDate.parse(regDate);
                            int years = (int) ChronoUnit.YEARS.between(founded, LocalDate.now());
                            builder.yearsInBusiness(years);
                        } catch (Exception e) {
                            // ignore parse errors
                        }
                    }

                    boolean active = "R".equals(company.path("status").asText());
                    builder.isActive(active);

                    allData.put("ariregister", response);
                }
            }
        } catch (Exception e) {
            log.debug("Ariregister API call failed: {}", e.getMessage());
            // Graceful degradation - continue without this data
        }
    }

    private void queryTaxDebt(String registryCode, RegistryData.RegistryDataBuilder builder,
                               Map<String, Object> allData) {
        // Estonian Tax Board public tax debt API
        // emta.ee provides a public check for active tax debts
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://www.emta.ee/api/tax-debtor/search?regCode=" + registryCode;

        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                boolean hasDebt = root.path("hasDebt").asBoolean(false);
                builder.taxDebt(hasDebt);

                if (hasDebt) {
                    BigDecimal amount = root.has("debtAmount")
                            ? new BigDecimal(root.get("debtAmount").asText("0"))
                            : null;
                    builder.taxDebtAmount(amount);
                }

                allData.put("taxDebt", response);
            }
        } catch (Exception e) {
            log.debug("Tax debt API call failed: {}", e.getMessage());
            // Graceful degradation - assume no debt info available
        }
    }

    private void queryProcurementHistory(String registryCode, RegistryData.RegistryDataBuilder builder,
                                          Map<String, Object> allData) {
        // Riigihangete Register (public procurement registry)
        // riigihanked.riik.ee has a public search
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://riigihanked.riik.ee/api/public/v1/suppliers?regCode=" + registryCode;

        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                int count = root.path("totalWins").asInt(0);
                builder.publicProcurementCount(count);
                allData.put("procurementHistory", response);
            }
        } catch (Exception e) {
            log.debug("Procurement registry API call failed: {}", e.getMessage());
            // Graceful degradation
        }
    }
}
