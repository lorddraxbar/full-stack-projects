package com.secphils.dto;

import com.secphils.entity.Project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        Long companyId,
        String companyName,
        Long serviceId,
        String serviceName,
        String name,
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
        Integer progress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime archivedAt,
        LocalDateTime deleteAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getCompany() != null ? p.getCompany().getId() : null,
                p.getCompany() != null ? p.getCompany().getName() : null,
                p.getService() != null ? p.getService().getId() : null,
                p.getService() != null ? p.getService().getName() : null,
                p.getName(), p.getScope(), p.getObjectives(), p.getDeliverables(),
                p.getStatus(), p.getTotalCost(), p.getRawMaterials(), p.getProductionOutput(),
                p.getWasteManagement(), p.getWasteMaterials(), p.getManufacturingProcedure(),
                p.getProductionFlowchartUrl(), p.getDueDate(), p.getProgress(),
                p.getCreatedAt(), p.getUpdatedAt(),
                p.getArchivedAt(), p.getDeleteAt());
    }
}
