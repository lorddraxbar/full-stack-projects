package com.secphils.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Sets (or re-sets) a company's authorized representative and their contact
 * details, in one scoped call. {@code repUserId} must be an active CLIENT of
 * that company — the wizard's picker and the project Overview editor both
 * choose from {@code GET /companies/{id}/team}, which is exactly that set.
 * Contact fields are optional: blank leaves the rep's user row untouched;
 * email is only changed when it differs and collides with nobody.
 */
public record AuthorizedRepRequest(
        @NotNull(message = "repUserId is required") Long repUserId,
        String fullName,
        String email,
        String phone
) {}
