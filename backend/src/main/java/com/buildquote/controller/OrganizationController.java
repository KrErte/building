package com.buildquote.controller;

import com.buildquote.dto.OrganizationDto;
import com.buildquote.entity.Organization;
import com.buildquote.entity.OrganizationMember;
import com.buildquote.entity.User;
import com.buildquote.repository.UserRepository;
import com.buildquote.security.UserPrincipal;
import com.buildquote.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        }

        Organization org = organizationService.createOrganization(name, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationDto.fromEntity(org));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> list(@AuthenticationPrincipal UserPrincipal principal) {
        List<OrganizationDto> orgs = organizationService.getUserOrganizations(principal.getId()).stream()
                .map(OrganizationDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(orgs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        Organization org = organizationService.getOrganization(id);
        return ResponseEntity.ok(OrganizationDto.fromEntity(org));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMember(@PathVariable UUID id,
                                        @RequestBody Map<String, String> request) {
        String email = request.get("email");
        String roleStr = request.getOrDefault("role", "MEMBER");
        OrganizationMember.MemberRole role = OrganizationMember.MemberRole.valueOf(roleStr);

        try {
            organizationService.addMember(id, email, role);
            return ResponseEntity.ok(Map.of("message", "Member added"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable UUID orgId, @PathVariable UUID userId) {
        try {
            organizationService.removeMember(orgId, userId);
            return ResponseEntity.ok(Map.of("message", "Member removed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/switch")
    public ResponseEntity<?> switchOrg(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        try {
            organizationService.switchOrganization(user, id);
            return ResponseEntity.ok(Map.of("message", "Switched to organization"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
