package com.secphils.repository;

import com.secphils.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    List<Project> findByCompanyId(Long companyId);

    List<Project> findByCompanyIdAndStatus(Long companyId, String status);

    List<Project> findByStatus(String status);

    long countByCompanyId(Long companyId);

    @Query("select p from Project p left join fetch p.company left join fetch p.service where p.id in :ids")
    List<Project> findByPageIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
