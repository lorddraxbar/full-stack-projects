package com.secphils.dto;

import com.secphils.entity.Message;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDateTime;

/** A row of the provider-only message trash listing. */
public record MessageTrashResponse(
        Long id,
        Long projectId,
        String projectName,
        String projectCompany,
        Long senderId,
        String senderName,
        String body,
        String attachmentFileName,
        /** 'CLIENT' or 'INTERNAL' — staff trash view shows real names (policy
         *  collapses PROVIDER identities to the brand; a client's own name stays). */
        String visibility,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        String deletedByName
) {
    public static MessageTrashResponse from(Message m) {
        return new MessageTrashResponse(m.getId(),
                m.getProject() != null ? m.getProject().getId() : null,
                m.getProject() != null ? m.getProject().getName() : null,
                m.getProject() != null && m.getProject().getCompany() != null
                        ? m.getProject().getCompany().getName() : null,
                m.getSender() != null ? m.getSender().getId() : null,
                DisplayNamePolicy.nameFor(m.getSender()),
                m.getBody(),
                m.getAttachmentFileName(),
                m.getVisibility() == null ? "CLIENT" : m.getVisibility(),
                m.getCreatedAt(),
                m.getDeletedAt(),
                m.getDeletedBy() != null ? DisplayNamePolicy.nameFor(m.getDeletedBy()) : null);
    }
}
