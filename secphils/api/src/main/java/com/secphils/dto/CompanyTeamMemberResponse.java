package com.secphils.dto;

import com.secphils.entity.User;

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
        String status;
        if (!Boolean.TRUE.equals(u.getIsActive())) {
            status = "Inactive";
        } else if (u.getLastLogin() == null) {
            status = "Invited";
        } else {
            status = "Active";
        }
        return new CompanyTeamMemberResponse(u.getId(), u.getFullName(), u.getEmail(),
                u.getRole(), status, u.getLastLogin());
    }
}