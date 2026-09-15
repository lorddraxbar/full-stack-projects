package com.secphils.repository;

import com.secphils.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUnsubscribeToken(String unsubscribeToken);

    List<User> findByRoleAndIsActive(String role, Boolean isActive);

    /** Active members of a company — recipients for company-wide notifications. */
    List<User> findByCompanyIdAndIsActiveTrue(Long companyId);

    /** All portal accounts belonging to a company (any status) — staff-side team browser. */
    List<User> findByCompanyIdOrderByEmail(Long companyId);

    /** Number of portal accounts currently holding this role name. */
    long countByRole(String role);

    // ---------- Client-company lifecycle (V39) ----------
    /** Provider guard: the company row an ADMIN belongs to IS SECPhils — never pause/delete it. */
    boolean existsByCompanyIdAndRole(Long companyId, String role);
    /** Pause: active CLIENT members lose sign-in (JwtAuthFilter/login gate on isActive), stamped.
     *  Already-removed members keep their own removal stamp. */
    @Modifying
    @Query("update User u set u.isActive = false, u.deactivatedAt = :stamp where u.companyId = :companyId and u.role = 'CLIENT' and u.isActive = true")
    int pauseClientsOfCompany(@Param("companyId") Long companyId, @Param("stamp") java.time.LocalDateTime stamp);
    /** Resume: ONLY members the pause deactivated (same stamp) come back — never a member
     *  the rep had removed before the pause. */
    @Modifying
    @Query("update User u set u.isActive = true, u.deactivatedAt = null where u.companyId = :companyId and u.role = 'CLIENT' and u.isActive = false and u.deactivatedAt = :stamp")
    int resumeClientsOfCompany(@Param("companyId") Long companyId, @Param("stamp") java.time.LocalDateTime stamp);
    /** Erasure: release every remaining member (row survives for history; access ends). */
    @Modifying
    @Query("update User u set u.isActive = false, u.deactivatedAt = coalesce(u.deactivatedAt, :stamp), u.companyId = null where u.companyId = :companyId")
    int releaseMembersOfCompany(@Param("companyId") Long companyId, @Param("stamp") java.time.LocalDateTime stamp);
    /** Erasure: pending set-your-own-password invites of a dying company are dead links. */
    @Modifying
    @Query("update User u set u.passwordResetToken = null, u.passwordResetExpiresAt = null where u.companyId = :companyId")
    int killPendingInvites(@Param("companyId") Long companyId);

    List<User> findByIsActiveTrue();

    Optional<User> findByPasswordResetToken(String passwordResetToken);
}
