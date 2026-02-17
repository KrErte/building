package com.buildquote.dto;

import lombok.Data;
import java.util.List;

@Data
public class OnboardingResponseDTO {
    private String companyName;
    private String currentEmail;
    private String currentPhone;
    private List<String> currentCategories;
    private List<String> currentServiceAreas;
    private boolean alreadyOnboarded;
}
