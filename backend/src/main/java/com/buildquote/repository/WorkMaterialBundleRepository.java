package com.buildquote.repository;

import com.buildquote.entity.WorkMaterialBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkMaterialBundleRepository extends JpaRepository<WorkMaterialBundle, Long> {

    List<WorkMaterialBundle> findByWorkCategory(String workCategory);

    List<WorkMaterialBundle> findByWorkCategoryIgnoreCase(String workCategory);

    long count();
}
