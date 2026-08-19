package com.secphils.dto;

import com.secphils.entity.DropdownCategory;
import com.secphils.entity.DropdownValue;

import java.util.List;

public record DropdownCategoryResponse(
        Long id,
        String name,
        String description,
        List<DropdownValueResponse> values
) {
    public record DropdownValueResponse(
            Long id,
            String value,
            String displayLabel,
            Integer sortOrder
    ) {
        public static DropdownValueResponse from(DropdownValue v) {
            return new DropdownValueResponse(v.getId(), v.getValue(), v.getDisplayLabel(), v.getSortOrder());
        }
    }

    public static DropdownCategoryResponse from(DropdownCategory c) {
        return new DropdownCategoryResponse(c.getId(), c.getName(), c.getDescription(),
                c.getValues().stream()
                        .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                        .map(DropdownValueResponse::from)
                        .toList());
    }
}
