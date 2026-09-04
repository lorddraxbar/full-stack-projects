package com.secphils.repository;

import com.secphils.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Announcement> findByAudienceOrderByCreatedAtDesc(String audience);

    List<Announcement> findByCreatedById(Long userId);

    @Query("select a from Announcement a left join fetch a.company left join fetch a.project left join fetch a.createdBy where a.company.id = :companyId order by a.createdAt desc")
    List<Announcement> findWithRefsByCompanyId(@org.springframework.data.repository.query.Param("companyId") Long companyId);
}
