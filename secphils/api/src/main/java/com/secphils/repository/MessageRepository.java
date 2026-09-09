package com.secphils.repository;

import com.secphils.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Messages still referencing the given object URLs (attachments share their
      * S3 object with the auto-created document row). */
    List<Message> findByAttachmentUrlIn(Iterable<String> urls);

    /**
     * Latest message (by id — IDENTITY means max id is newest) for each of the
     * given projects, newest first per project. Used by the projects list page
     * to show "latest update" + date. Trashed messages (V33) never preview.
     */
    @Query(value = "SELECT m.* FROM messages m " +
            "WHERE m.id IN (" +
            "  SELECT max(id) FROM messages WHERE project_id IN :projectIds " +
            "    AND deleted_at IS NULL GROUP BY project_id)",
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
            "    AND COALESCE(visibility,'CLIENT') <> 'INTERNAL' AND deleted_at IS NULL GROUP BY project_id)",
           nativeQuery = true)
    List<Message> findLatestNonInternalPerProject(@org.springframework.data.repository.query.Param("projectIds")
                                                  Collection<Long> projectIds);

    /**
     * Message count per project (project_id → count), newest projects may have
     * zero rows. Used by the Messages inbox to show the per-conversation badge
     * without one round-trip per project. CLIENT viewers use the internal-
     * excluding variant so a staff-only thread never inflates a client's count.
     * Trashed messages (V33) never count.
     */
    @Query(value = "SELECT project_id AS \"projectId\", count(*) AS \"cnt\" FROM messages " +
            "WHERE project_id IN :projectIds AND deleted_at IS NULL GROUP BY project_id",
           nativeQuery = true)
    List<java.util.Map<String, Object>> countPerProject(@org.springframework.data.repository.query.Param("projectIds")
                                                         Collection<Long> projectIds);

    /** Client variant of {@link #countPerProject}: excludes INTERNAL messages. */
    @Query(value = "SELECT project_id AS \"projectId\", count(*) AS \"cnt\" FROM messages " +
            "WHERE project_id IN :projectIds AND COALESCE(visibility,'CLIENT') <> 'INTERNAL' " +
            "AND deleted_at IS NULL GROUP BY project_id",
           nativeQuery = true)
    List<java.util.Map<String, Object>> countNonInternalPerProject(@org.springframework.data.repository.query.Param("projectIds")
                                                                    Collection<Long> projectIds);

    @Query("select m from Message m left join fetch m.project left join fetch m.sender where m.project.id = :projectId and m.deletedAt is null order by m.createdAt asc")
    List<Message> findWithRefsByProjectId(@org.springframework.data.repository.query.Param("projectId") Long projectId);

    // ---------- provider-only trash (V33) ----------

    @Query("select m from Message m left join fetch m.project left join fetch m.sender left join fetch m.deletedBy where m.deletedAt is not null order by m.deletedAt desc")
    List<Message> findWithRefsDeleted();

    List<Message> findByDeletedAtBefore(java.time.LocalDateTime cutoff);

    @Query("select m from Message m left join fetch m.project left join fetch m.sender left join fetch m.deletedBy where m.id = :id")
    java.util.Optional<Message> findWithRefsById(@org.springframework.data.repository.query.Param("id") Long id);
}
