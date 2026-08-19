package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectRequest(
        @NotNull Long companyId,
        Long serviceId,
        @NotBlank String name,
        String scope,
        String objectives,
        String deliverables,
        String status,
        BigDecimal totalCost,
        String rawMaterials,
        String productionOutput,
        String wasteManagement,
        String wasteMaterials,
        String manufacturingProcedure,
        String productionFlowchartUrl,
        LocalDate dueDate,
        Integer progress
) {}
