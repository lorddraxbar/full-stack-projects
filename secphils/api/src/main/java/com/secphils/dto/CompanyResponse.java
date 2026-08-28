package com.secphils.dto;

import com.secphils.entity.Company;
import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
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
        String authorizedRepName,
        String authorizedRepPhone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CompanyResponse from(Company c) {
        User rep = c.getAuthorizedRep();
        return new CompanyResponse(c.getId(), c.getName(), c.getLocation(), c.getOwner(),
                c.getOwnerPhone(), c.getDescription(), c.getTagline(), c.getIndustrySectors(), c.getHeadquarters(),
                c.getPhone(), c.getEmail(), c.getWebsite(), c.getSocialLinks(), c.getTaxNumber(),
                c.getBankingDetails(), c.getOperationalFields(), c.getBrandPrimary(),
                c.getBrandSecondary(), c.getLogoUrl(), c.getContactDetails(),
                rep != null ? rep.getId() : null,
                DisplayNamePolicy.nameFor(rep),
                rep != null ? rep.getPhone() : null,
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
