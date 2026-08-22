package com.secphils.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invite a new member to the caller's company (Team & Invitations). */
public record TeamInviteRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email String email,
        @Size(max = 40) String role
) {}