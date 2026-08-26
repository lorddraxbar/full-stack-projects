package com.secphils.repository;

import com.secphils.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleAndIsActive(String role, Boolean isActive);

    /** Active members of a company — recipients for company-wide notifications. */
    List<User> findByCompanyIdAndIsActiveTrue(Long companyId);

    /** All portal accounts belonging to a company (any status) — staff-side team browser. */
    List<User> findByCompanyIdOrderByEmail(Long companyId);

    /** Number of portal accounts currently holding this role name. */
    long countByRole(String role);

    List<User> findByIsActiveTrue();

    Optional<User> findByPasswordResetToken(String passwordResetToken);
}
