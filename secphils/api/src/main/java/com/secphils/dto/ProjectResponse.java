package com.secphils.dto;

import com.secphils.entity.Project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        Long companyId,
        String companyName,
        Long serviceId,
        String serviceName,
        String name,
        String notes,
        String objectives,
        String deliverables,
        String address,
        String status,
        BigDecimal totalCost,
        String rawMaterials,
        String productionOutput,
        String wasteManagement,
        String wasteMaterials,
        String manufacturingProcedure,
        String productionFlowchartUrl,
        Integer progress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime archivedAt,
        LocalDateTime deleteAt,
        String latestUpdateBody,
        LocalDateTime latestUpdateAt,
        String latestUpdateSender,
        String latestUpdateVisibility,
        Boolean latestHasFile,
        Integer messageCount
) {
    /** Entity-only — used for single-project reads; no latest-message enrichment. */
    public static ProjectResponse from(Project p) {
        return from(p, null, null);
    }

    /** List-page use: latest-update body + timestamp (nulls when none). */
    public static ProjectResponse from(Project p, String latestUpdateBody, LocalDateTime latestUpdateAt) {
        return from(p, latestUpdateBody, latestUpdateAt, null, null, null, null);
    }

    /**
     * Messages-inbox use: carries everything the conversation list needs so it can
     * build previews from one batched /projects call instead of one
     * /messages?projectId= round-trip per project.
     *
     * @param latestUpdateSender      display name of the latest message's author
     *                                (brand-masked for provider senders, real name
     *                                for internal messages — mirrors MessageController)
     * @param latestUpdateVisibility  "CLIENT" or "INTERNAL" (null when no message)
     * @param latestHasFile           whether the latest message carries an attachment
     * @param messageCount            total visible messages for the project (client:
     *                                internal excluded) — drives the count badge
     */
    public static ProjectResponse from(Project p, String latestUpdateBody, LocalDateTime latestUpdateAt,
                                       String latestUpdateSender, String latestUpdateVisibility,
                                       Boolean latestHasFile, Integer messageCount) {
        return new ProjectResponse(
                p.getId(),
                p.getCompany() != null ? p.getCompany().getId() : null,
                p.getCompany() != null ? p.getCompany().getName() : null,
                p.getService() != null ? p.getService().getId() : null,
                p.getService() != null ? p.getService().getName() : null,
                p.getName(), p.getNotes(), p.getObjectives(), p.getDeliverables(),
                p.getAddress(),
                p.getStatus(), p.getTotalCost(), p.getRawMaterials(), p.getProductionOutput(),
                p.getWasteManagement(), p.getWasteMaterials(), p.getManufacturingProcedure(),
                p.getProductionFlowchartUrl(), p.getProgress(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getCompletedAt(),
                p.getArchivedAt(), p.getDeleteAt(),
                latestUpdateBody,
                latestUpdateAt,
                latestUpdateSender,
                latestUpdateVisibility,
                latestHasFile,
                messageCount);
    }
}
