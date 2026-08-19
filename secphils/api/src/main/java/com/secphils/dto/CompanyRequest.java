package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompanyRequest(
        @NotBlank String name,
        String location,
        String owner,
        String description,
        @NotNull Long authorizedRepId
) {}
