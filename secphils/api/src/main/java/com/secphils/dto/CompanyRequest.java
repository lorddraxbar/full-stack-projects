package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyRequest(
        @NotBlank String name,
        String location,
        String owner,
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
        Long authorizedRepId
) {}
