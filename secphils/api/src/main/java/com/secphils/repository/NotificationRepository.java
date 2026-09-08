package com.secphils.repository;

import com.secphils.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    /** Message-trash (V33): a trashed message's bell rows die with it — the
     *  notification body carries the message text, so stale rows would keep
     *  the content readable. */
    @org.springframework.data.jpa.repository.Modifying
    void deleteByEntityTypeAndEntityId(String entityType, Long entityId);
}
