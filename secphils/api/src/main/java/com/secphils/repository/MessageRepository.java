package com.secphils.repository;

import com.secphils.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    List<Message> findBySenderId(Long senderId);

    /**
     * Latest message (by id — IDENTITY means max id is newest) for each of the
     * given projects, newest first per project. Used by the projects list page
     * to show "latest update" + date.
     */
    @Query(value = "SELECT m.* FROM messages m " +
            "WHERE m.id IN (" +
            "  SELECT max(id) FROM messages WHERE project_id IN :projectIds GROUP BY project_id)",
           nativeQuery = true)
    List<Message> findLatestPerProject(@org.springframework.data.repository.query.Param("projectIds")
                                       Collection<Long> projectIds);

    /**
     * Latest message per project, excluding INTERNAL messages. Used to build the
     * "latest update" preview for CLIENT-role viewers so an internal staff
     * message can never surface in a client-facing project list.
     */
    @Query(value = "SELECT m.* FROM messages m " +
            "WHERE m.id IN (" +
            "  SELECT max(id) FROM messages WHERE project_id IN :projectIds " +
            "    AND COALESCE(visibility,'CLIENT') <> 'INTERNAL' GROUP BY project_id)",
           nativeQuery = true)
    List<Message> findLatestNonInternalPerProject(@org.springframework.data.repository.query.Param("projectIds")
                                                  Collection<Long> projectIds);
}
