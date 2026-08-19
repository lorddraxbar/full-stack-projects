package com.secphils.repository;

import com.secphils.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByProjectId(Long projectId);

    List<Review> findByCustomerUserId(Long reviewerId);

    List<Review> findByStatus(String status);
}
