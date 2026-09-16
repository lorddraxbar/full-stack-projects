package com.secphils.repository;

import com.secphils.entity.EmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, Long> {

    Optional<EmailSuppression> findByEmailIgnoreCaseAndCategory(String email, String category);
}
