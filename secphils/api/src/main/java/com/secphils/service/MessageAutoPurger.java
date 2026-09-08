package com.secphils.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly sweep: messages trashed beyond the retention window are purged
 * automatically (row + attachment object when no document row mirrors it).
 * No password — the window is what bounds the removal. Mirrors
 * DocumentAutoPurger; the two run independently.
 */
@Component
public class MessageAutoPurger {

    private static final Logger log = LoggerFactory.getLogger(MessageAutoPurger.class);

    private final MessageTrashService trash;

    public MessageAutoPurger(MessageTrashService trash) {
        this.trash = trash;
    }

    @Scheduled(fixedDelay = 3600_000L, initialDelay = 90_000L)
    public void purgeExpired() {
        try {
            int purged = trash.purgeExpired();
            if (purged > 0) {
                log.info("Message auto-purge removed {} message(s) older than the trash window", purged);
            }
        } catch (Exception e) {
            log.error("Message trash auto-purge failed (will retry on the next tick)", e);
        }
    }
}
