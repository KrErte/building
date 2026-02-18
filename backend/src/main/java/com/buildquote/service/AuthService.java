package com.buildquote.service;

import com.buildquote.dto.AuthRequest;
import com.buildquote.dto.AuthResponse;
import com.buildquote.entity.*;
import com.buildquote.repository.*;
import com.buildquote.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse login(AuthRequest.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Vale e-post või parool"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Vale e-post või parool");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return generateAuthResponse(user, "Sisselogimine õnnestus");
    }

    @Transactional
    public AuthResponse register(AuthRequest.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("See e-posti aadress on juba kasutusel");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .company(request.getCompany())
                .phone(request.getPhone())
                .role(User.UserRole.USER)
                .plan(User.SubscriptionPlan.FREE)
                .emailVerified(false)
                .projectsThisMonth(0)
                .rfqsThisMonth(0)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());

        return generateAuthResponse(user, "Konto loodud edukalt");
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new IllegalArgumentException("Refresh token expired");
        }

        // Revoke old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        return generateAuthResponse(user, "Token refreshed");
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void logoutAll(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();

        passwordResetTokenRepository.save(token);
        log.info("Password reset token created for user: {}", email);
        return tokenValue;
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenAndUsedFalse(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // Revoke all refresh tokens for security
        refreshTokenRepository.revokeAllByUser(user);

        log.info("Password reset for user: {}", user.getEmail());
    }

    @Transactional
    public String createEmailVerificationToken(User user) {
        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        emailVerificationTokenRepository.save(token);
        return tokenValue;
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification token expired");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        emailVerificationTokenRepository.delete(token);
        log.info("Email verified for user: {}", user.getEmail());
    }

    private AuthResponse generateAuthResponse(User user, String message) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshTokenValue = tokenProvider.generateRefreshTokenValue();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(tokenProvider.getRefreshTokenExpirationMs() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .user(AuthResponse.UserDto.fromEntity(user))
                .token(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(tokenProvider.getAccessTokenExpirationMs() / 1000)
                .message(message)
                .build();
    }
}
