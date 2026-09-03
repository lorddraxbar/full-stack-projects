package com.secphils.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly sweep: documents trashed more than 7 days ago are purged
 * automatically (row + comments + S3 object, with the shared-object guard).
 * No password — the retention window is what bounds the removal.
 */
@Component
public class DocumentAutoPurger {

    private static final Logger log = LoggerFactory.getLogger(DocumentAutoPurger.class);

    private final DocumentTrashService trash;

    public DocumentAutoPurger(DocumentTrashService trash) {
        this.trash = trash;
    }

    @Scheduled(fixedDelay = 3600_000L, initialDelay = 60_000L)
    public void purgeExpired() {
        try {
            int purged = trash.purgeExpired();
            if (purged > 0) {
                log.info("Auto-purge removed {} document(s) older than the trash window", purged);
            }
        } catch (Exception e) {
            log.error("Trash auto-purge failed (will retry on the next tick)", e);
        }
    }
}
