package com.secphils.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Staff/admin invites a NEW client user to a customer company from the project wizard
 * ("add a new authorized representative"). The invitee is created as an inactive CLIENT
 * on that company and, when {@code setAsRep} is true, is also set as the company's
 * authorized representative so the project can proceed.
 */
public record CustomerRepInviteRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email String email,
        boolean setAsRep
) {}
