package com.buildquote.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompanyDto {
    private String id;
    private String companyName;
    private String contactPerson;
    private String email;
    private String phone;
    private String website;
    private String address;
    private String city;
    private String county;
    private List<String> categories;
    private List<String> serviceAreas;
    private String source;
    private BigDecimal googleRating;
    private Integer googleReviewCount;
    private Integer trustScore;
    private Boolean isVerified;
}
