package com.buildquote.dto;

import com.buildquote.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UserDto user;
    private String token;
    private String refreshToken;
    private Long expiresIn;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String company;
        private String phone;
        private String role;
        private String plan;
        private boolean emailVerified;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;

        public static UserDto fromEntity(User user) {
            return UserDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .company(user.getCompany())
                    .phone(user.getPhone())
                    .role(user.getRole().name())
                    .plan(user.getPlan().name())
                    .emailVerified(user.isEmailVerified())
                    .lastLoginAt(user.getLastLoginAt())
                    .createdAt(user.getCreatedAt())
                    .build();
        }
    }
}
