package com.buildquote.repository;

import com.buildquote.entity.Pipeline;
import com.buildquote.entity.PipelineStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineStepRepository extends JpaRepository<PipelineStep, UUID> {

    List<PipelineStep> findByPipelineOrderByStepOrderAsc(Pipeline pipeline);
}
