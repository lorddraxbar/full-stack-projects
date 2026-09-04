package com.secphils.repository;

import com.secphils.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByProjectId(Long projectId);

    List<Review> findByCustomerUserId(Long reviewerId);

    List<Review> findByStatus(String status);

    @Query("select r from Review r left join fetch r.project left join fetch r.customerUser")
    List<Review> findWithRefsAll();

    @Query("select r from Review r left join fetch r.project left join fetch r.customerUser where r.status = :status")
    List<Review> findWithRefsByStatus(@org.springframework.data.repository.query.Param("status") String status);

    @Query("select r from Review r left join fetch r.project left join fetch r.customerUser where r.project.id = :id")
    java.util.Optional<Review> findWithRefsByProjectId(@org.springframework.data.repository.query.Param("id") Long id);
}
