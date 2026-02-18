package com.buildquote.repository;

import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectStageRepository extends JpaRepository<ProjectStage, UUID> {

    List<ProjectStage> findByProjectOrderByStageOrderAsc(Project project);
}
