package com.secphils.dto;

import com.secphils.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long projectId,
        Long customerId,
        String customerName,
        Integer rating,
        String title,
        String body,
        String status,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getId(),
                r.getProject() != null ? r.getProject().getId() : null,
                r.getCustomerUser() != null ? r.getCustomerUser().getId() : null,
                r.getCustomerUser() != null ? r.getCustomerUser().getFullName() : null,
                r.getRating(), r.getTitle(), r.getBody(), r.getStatus(), r.getCreatedAt());
    }
}
