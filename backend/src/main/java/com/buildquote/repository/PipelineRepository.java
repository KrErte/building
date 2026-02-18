package com.buildquote.repository;

import com.buildquote.entity.Pipeline;
import com.buildquote.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    List<Pipeline> findByUserOrderByCreatedAtDesc(User user);

    List<Pipeline> findByProjectId(UUID projectId);

    List<Pipeline> findByStatus(Pipeline.PipelineStatus status);
}
