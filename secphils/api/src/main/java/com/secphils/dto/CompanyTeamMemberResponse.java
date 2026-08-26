package com.secphils.dto;

import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;

/**
 * A member of the caller's company (Settings - Team &amp; Invitations UI).
 * {@code status} is derived: Inactive / Invited (never signed in) / Active.
 */
public record CompanyTeamMemberResponse(
        Long id,
        String name,
        String email,
        String role,
        String status,
        java.time.LocalDateTime lastLogin
) {
    public static CompanyTeamMemberResponse from(User u) {
        return new CompanyTeamMemberResponse(u.getId(), DisplayNamePolicy.nameFor(u), u.getEmail(),
                u.getRole(), statusFor(u), u.getLastLogin());
    }

    /**
     * Staff/admin-facing mapping (e.g. the project wizard picking a client user for a
     * customer company): real names always — provider-identity anonymization is a
     * client-facing policy only (see DisplayNamePolicy javadoc).
     */
    public static CompanyTeamMemberResponse fromStaff(User u) {
        String name = u.getFullName();
        if (name == null || name.isBlank()) name = u.getEmail();
        return new CompanyTeamMemberResponse(u.getId(), name, u.getEmail(),
                u.getRole(), statusFor(u), u.getLastLogin());
    }

    private static String statusFor(User u) {
        if (!Boolean.TRUE.equals(u.getIsActive())) return "Inactive";
        return u.getLastLogin() == null ? "Invited" : "Active";
    }
}