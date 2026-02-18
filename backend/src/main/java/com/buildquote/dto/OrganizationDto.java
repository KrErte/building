package com.buildquote.dto;

import com.buildquote.entity.Organization;
import com.buildquote.entity.OrganizationMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {

    private UUID id;
    private String name;
    private String slug;
    private String plan;
    private UUID ownerId;
    private Integer maxMembers;
    private Integer maxProjectsPerMonth;
    private List<MemberDto> members;
    private String createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDto {
        private UUID userId;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private String joinedAt;

        public static MemberDto fromEntity(OrganizationMember member) {
            return MemberDto.builder()
                    .userId(member.getUser().getId())
                    .email(member.getUser().getEmail())
                    .firstName(member.getUser().getFirstName())
                    .lastName(member.getUser().getLastName())
                    .role(member.getRole().name())
                    .joinedAt(member.getJoinedAt() != null ? member.getJoinedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                    .build();
        }
    }

    public static OrganizationDto fromEntity(Organization org) {
        List<MemberDto> memberDtos = org.getMembers() != null
                ? org.getMembers().stream().map(MemberDto::fromEntity).collect(Collectors.toList())
                : List.of();

        return OrganizationDto.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .plan(org.getPlan())
                .ownerId(org.getOwner().getId())
                .maxMembers(org.getMaxMembers())
                .maxProjectsPerMonth(org.getMaxProjectsPerMonth())
                .members(memberDtos)
                .createdAt(org.getCreatedAt() != null ? org.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .build();
    }
}
