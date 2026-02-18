package com.buildquote.repository;

import com.buildquote.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    List<Organization> findByOwnerId(UUID ownerId);

    boolean existsBySlug(String slug);
}
