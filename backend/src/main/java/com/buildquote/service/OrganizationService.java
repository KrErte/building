package com.buildquote.service;

import com.buildquote.entity.Organization;
import com.buildquote.entity.OrganizationMember;
import com.buildquote.entity.User;
import com.buildquote.repository.OrganizationMemberRepository;
import com.buildquote.repository.OrganizationRepository;
import com.buildquote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional
    public Organization createOrganization(String name, User owner) {
        String slug = generateSlug(name);

        Organization org = Organization.builder()
                .name(name)
                .slug(slug)
                .plan(owner.getPlan().name())
                .owner(owner)
                .maxMembers(getMaxMembers(owner.getPlan()))
                .maxProjectsPerMonth(getMaxProjects(owner.getPlan()))
                .members(new ArrayList<>())
                .build();

        org = organizationRepository.save(org);

        // Add owner as member
        OrganizationMember ownerMember = OrganizationMember.builder()
                .organization(org)
                .user(owner)
                .role(OrganizationMember.MemberRole.OWNER)
                .build();
        org.getMembers().add(ownerMember);
        org = organizationRepository.save(org);

        // Set as user's current org
        owner.setCurrentOrganizationId(org.getId());
        userRepository.save(owner);

        log.info("Organization '{}' created by {}", name, owner.getEmail());
        return org;
    }

    @Transactional
    public OrganizationMember addMember(UUID orgId, String email, OrganizationMember.MemberRole role) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (memberRepository.existsByOrganizationIdAndUserId(orgId, user.getId())) {
            throw new RuntimeException("User is already a member");
        }

        if (org.getMembers().size() >= org.getMaxMembers()) {
            throw new RuntimeException("Organization has reached maximum member count");
        }

        OrganizationMember member = OrganizationMember.builder()
                .organization(org)
                .user(user)
                .role(role)
                .build();

        member = memberRepository.save(member);
        log.info("Added {} as {} to organization '{}'", email, role, org.getName());
        return member;
    }

    @Transactional
    public void removeMember(UUID orgId, UUID userId) {
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(orgId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member.getRole() == OrganizationMember.MemberRole.OWNER) {
            throw new RuntimeException("Cannot remove the organization owner");
        }

        memberRepository.delete(member);
        log.info("Removed user {} from organization {}", userId, orgId);
    }

    public Organization getOrganization(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
    }

    public List<Organization> getUserOrganizations(UUID userId) {
        List<OrganizationMember> memberships = memberRepository.findByUserId(userId);
        return memberships.stream()
                .map(OrganizationMember::getOrganization)
                .toList();
    }

    @Transactional
    public void switchOrganization(User user, UUID orgId) {
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, user.getId())) {
            throw new RuntimeException("User is not a member of this organization");
        }
        user.setCurrentOrganizationId(orgId);
        userRepository.save(user);
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        String slug = base;
        int counter = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }

    private int getMaxMembers(User.SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> 3;
            case PRO -> 10;
            case ENTERPRISE -> 50;
        };
    }

    private int getMaxProjects(User.SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> 5;
            case PRO -> 50;
            case ENTERPRISE -> 500;
        };
    }
}
