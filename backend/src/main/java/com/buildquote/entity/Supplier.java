package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Entity
@Table(name = "suppliers")
@Data
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String googlePlaceId;
    private String registryCode;

    @Column(nullable = false)
    private String companyName;

    private String contactPerson;
    private String email;
    private String phone;
    private String website;
    private String address;
    private String city;
    private String county;

    @Column(length = 2000)
    @Convert(converter = StringArrayConverter.class)
    private String[] categories;

    @Column(length = 2000)
    @Convert(converter = StringArrayConverter.class)
    private String[] serviceAreas;

    private String source;

    private BigDecimal googleRating;
    private Integer googleReviewCount;
    private Integer trustScore;

    private String emtakCode;
    private Boolean isVerified = false;

    // Onboarding fields
    @Column(unique = true)
    private String onboardingToken;
    private LocalDateTime onboardingEmailSentAt;
    private LocalDateTime onboardedAt;
    private String additionalInfo;

    private LocalDateTime lastRfqSentAt;
    private Integer totalRfqsSent = 0;
    private Integer totalBidsReceived = 0;
    private BigDecimal avgResponseTimeHours;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}

@Converter
class StringArrayConverter implements AttributeConverter<String[], String> {
    @Override
    public String convertToDatabaseColumn(String[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return null;
        }
        return String.join(",", attribute);
    }

    @Override
    public String[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new String[0];
        }
        return dbData.split(",");
    }
}
