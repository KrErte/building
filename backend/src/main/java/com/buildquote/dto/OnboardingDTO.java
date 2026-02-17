package com.buildquote.dto;

import lombok.Data;
import java.util.List;

@Data
public class OnboardingDTO {
    private String companyName;
    private String contactPerson;
    private String email;
    private String phone;
    private List<String> categories;
    private List<String> serviceAreas;
    private String additionalInfo;
}
