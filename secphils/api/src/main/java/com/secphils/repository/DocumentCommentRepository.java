package com.secphils.repository;

import com.secphils.entity.DocumentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {

    List<DocumentComment> findByDocumentIdOrderByCreatedAtAsc(Long documentId);

    @Query("select c from DocumentComment c left join fetch c.document left join fetch c.user where c.document.id = :id order by c.createdAt asc")
    List<DocumentComment> findWithRefsByDocumentId(@org.springframework.data.repository.query.Param("id") Long id);
}
