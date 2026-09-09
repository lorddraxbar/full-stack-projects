package com.secphils.dto;

import com.secphils.common.DropdownProtection;
import com.secphils.entity.DropdownCategory;
import com.secphils.entity.DropdownValue;

import java.util.List;

public record DropdownCategoryResponse(
        Long id,
        String name,
        String description,
        boolean protectedCategory,
        List<DropdownValueResponse> values
) {
    public record DropdownValueResponse(
            Long id,
            String value,
            String displayLabel,
            Integer sortOrder,
            boolean protectedValue
    ) {
        /** Caller must be inside a transaction (category is lazy). */
        public static DropdownValueResponse from(DropdownValue v) {
            return new DropdownValueResponse(v.getId(), v.getValue(), v.getDisplayLabel(), v.getSortOrder(),
                    DropdownProtection.isLockedValue(v.getCategory().getName(), v.getValue()));
        }
    }

    public static DropdownCategoryResponse from(DropdownCategory c) {
        return new DropdownCategoryResponse(c.getId(), c.getName(), c.getDescription(),
                Boolean.TRUE.equals(c.getProtectedFlag()),
                c.getValues().stream()
                        .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                        .map(DropdownValueResponse::from)
                        .toList());
    }
}
