package com.secphils.repository;

import com.secphils.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    List<Message> findBySenderId(Long senderId);
}
