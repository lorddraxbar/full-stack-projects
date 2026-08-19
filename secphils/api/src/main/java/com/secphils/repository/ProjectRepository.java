package com.secphils.repository;

import com.secphils.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    List<Project> findByCompanyId(Long companyId);

    List<Project> findByCompanyIdAndStatus(Long companyId, String status);

    List<Project> findByStatus(String status);

    long countByCompanyId(Long companyId);
}
