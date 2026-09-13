package com.secphils.repository;

import com.secphils.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    boolean existsByCategory(String category);

    @Query("select a from Announcement a left join fetch a.company left join fetch a.project left join fetch a.createdBy where a.company.id = :companyId order by a.createdAt desc")
    List<Announcement> findWithRefsByCompanyId(@org.springframework.data.repository.query.Param("companyId") Long companyId);

    /** Provider view: EVERY row, including orphaned ones (company cleared by a
     *  client-company erasure, V39 SET NULL) — those survive as provider-wide
     *  history and stay manageable. CLIENTs never reach this (list() scopes them). */
    @Query("select a from Announcement a left join fetch a.company left join fetch a.project left join fetch a.createdBy order by a.createdAt desc")
    List<Announcement> findWithRefsAll();
}
