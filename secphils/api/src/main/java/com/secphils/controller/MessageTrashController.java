package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.MessageTrashRequest;
import com.secphils.dto.MessageTrashResponse;
import com.secphils.entity.Message;
import com.secphils.repository.MessageRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.MessageTrashService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * The provider-only message trash (V33) — the supported erasure path. Clients
 * never reach any of these routes: the service 403s CLIENT role on every one,
 * and no client surface links to them. The thread itself is the record; this
 * surface exists for the rare legal/erasure case, with a required reason in
 * the audit trail and the same password discipline as the document trash.
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageTrashController {

    private final MessageRepository messages;
    private final MessageTrashService trash;
    private final AuditService auditService;

    public MessageTrashController(MessageRepository messages, MessageTrashService trash,
                                  AuditService auditService) {
        this.messages = messages;
        this.trash = trash;
        this.auditService = auditService;
    }

    /** Trash listing (provider staff only). Admin sees every company. */
    @GetMapping("/trash")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageTrashResponse>> listTrash() {
        AuthUser actor = CurrentUser.require();
        if (actor.isClient()) throw ApiException.forbidden("Message trash is staff-only");
        List<Message> rows = messages.findWithRefsDeleted().stream()
                .filter(m -> actor.isAdmin()
                        || (m.getProject() != null && m.getProject().getCompany() != null
                            && m.getProject().getCompany().getId().equals(actor.getCompanyId())))
                .toList();
        return ResponseEntity.ok(rows.stream().map(MessageTrashResponse::from).toList());
    }

    /** Move a message to the trash. Reason required; lands in the audit trail. */
    @DeleteMapping("/{id}/trash")
    @Transactional
    public ResponseEntity<Void> toTrash(@PathVariable Long id,
                                        @Valid @RequestBody MessageTrashRequest req,
                                        HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        trash.delete(actor, id, req.reason()); // service writes the MESSAGE_DELETE audit
        return ResponseEntity.noContent().build();
    }

    /** Restore a trashed message to its thread. Provider staff (own company). */
    @PostMapping("/{id}/restore")
    @Transactional
    public ResponseEntity<MessageTrashResponse> restore(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Message m = trash.restore(actor, id); // service writes the MESSAGE_RESTORE audit
        return ResponseEntity.ok(MessageTrashResponse.from(m));
    }

    /** Purge one trashed message now (row + unmirrored attachment object).
     *  Password re-authentication required, same as the document trash. */
    @DeleteMapping("/{id}/permanent")
    @Transactional
    public ResponseEntity<Void> deletePermanent(@PathVariable Long id,
                                                @Valid @RequestBody com.secphils.dto.HardDeleteUserRequest req,
                                                HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Message m = messages.findWithRefsById(id)
                .orElseThrow(() -> ApiException.notFound("Message"));
        if (m.getDeletedAt() == null) {
            throw ApiException.badRequest("Message is not in the trash");
        }
        trash.hardDeleteOnePublic(actor, m, req.password()); // service writes the audit
        return ResponseEntity.noContent().build();
    }

    /** Empty the (visible) message trash: purge every trashed message of the
     *  actor's company (admin: all companies). Password re-authentication. */
    @PostMapping("/trash/empty")
    @Transactional
    public ResponseEntity<Map<String, Object>> emptyTrash(
            @Valid @RequestBody com.secphils.dto.HardDeleteUserRequest req,
            HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        int purged = trash.hardDeleteAll(actor, req.password());
        auditService.audit(actor, "MESSAGE_TRASH_EMPTY", "Message", null,
                "Purged: " + purged, http);
        return ResponseEntity.ok(Map.of("purged", purged));
    }
}
