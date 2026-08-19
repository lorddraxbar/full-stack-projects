package com.secphils.repository;

import com.secphils.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Announcement> findByAudienceOrderByCreatedAtDesc(String audience);

    List<Announcement> findByCreatedById(Long userId);
}
