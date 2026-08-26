package com.secphils.dto;

import com.secphils.entity.ProjectTeamMember;
import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDateTime;

public record TeamMemberResponse(
        Long userId,
        String email,
        String fullName,
        String role,
        LocalDateTime assignedAt
) {
    public static TeamMemberResponse from(ProjectTeamMember m) {
        User u = m.getUser();
        return new TeamMemberResponse(u.getId(), u.getEmail(), DisplayNamePolicy.nameFor(u), u.getRole(), m.getAssignedAt());
    }
}
