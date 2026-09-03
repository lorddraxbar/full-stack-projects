package com.secphils.repository;

import com.secphils.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByProjectId(Long projectId);

    List<Document> findByProjectIdIn(Iterable<Long> projectIds);

    List<Document> findByUploaderId(Long userId);

    List<Document> findByDeletedAtIsNotNull();

    List<Document> findByDeletedAtIsNotNullAndProjectIdIn(Iterable<Long> projectIds);

    List<Document> findByDeletedAtBefore(java.time.LocalDateTime cutoff);
}
