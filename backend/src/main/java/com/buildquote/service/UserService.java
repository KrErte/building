package com.buildquote.service;

import com.buildquote.entity.User;
import com.buildquote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(String email, String password, String firstName, String lastName,
                           String company, String phone, User.UserRole role, User.SubscriptionPlan plan) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .company(company)
                .phone(phone)
                .role(role)
                .plan(plan)
                .emailVerified(false)
                .projectsThisMonth(0)
                .rfqsThisMonth(0)
                .build();

        User saved = userRepository.save(user);
        log.info("Created user: {} ({})", email, role);
        return saved;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void updateLastLogin(User user) {
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    public long countByRole(User.UserRole role) {
        return userRepository.countByRole(role);
    }

    public long countByPlan(User.SubscriptionPlan plan) {
        return userRepository.countByPlan(plan);
    }
}
