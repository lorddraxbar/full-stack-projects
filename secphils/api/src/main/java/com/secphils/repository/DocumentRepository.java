package com.secphils.repository;

import com.secphils.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select d from Document d left join fetch d.project left join fetch d.uploader left join fetch d.deletedBy")
    List<Document> findWithRefs();

    @Query("select d from Document d left join fetch d.project left join fetch d.uploader left join fetch d.deletedBy where d.project.id = :id")
    List<Document> findWithRefsByProjectId(@org.springframework.data.repository.query.Param("id") Long id);

    @Query("select d from Document d left join fetch d.project left join fetch d.uploader left join fetch d.deletedBy where d.project.id in :ids")
    List<Document> findWithRefsByProjectIdIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    @Query("select d from Document d left join fetch d.project left join fetch d.uploader left join fetch d.deletedBy where d.deletedAt is not null")
    List<Document> findWithRefsDeleted();
}
