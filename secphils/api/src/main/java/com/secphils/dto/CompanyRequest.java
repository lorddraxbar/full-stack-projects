package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyRequest(
        @NotBlank String name,
        String location,
        String owner,
        String ownerPhone,
        String description,
        String tagline,
        String industrySectors,
        String headquarters,
        String phone,
        String email,
        String website,
        String socialLinks,
        String taxNumber,
        String bankingDetails,
        String operationalFields,
        String brandPrimary,
        String brandSecondary,
        String logoUrl,
        String contactDetails,
        Long authorizedRepId,
        /** New-customer wizard: full name of the authorized rep whose CLIENT
         *  account is created and invited when no authorizedRepId is given. */
        String repName,
        /** Optional phone for the authorized rep — the new-customer wizard
         *  creates the rep account with it; on updates it fills in the
         *  selected rep's user row when blank. */
        String repPhone
) {}
