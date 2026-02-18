package com.buildquote.repository;

import com.buildquote.entity.Project;
import com.buildquote.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserOrderByCreatedAtDesc(User user);

    Optional<Project> findByIdAndUser(UUID id, User user);

    List<Project> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
