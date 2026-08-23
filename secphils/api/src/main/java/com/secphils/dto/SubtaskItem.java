package com.secphils.dto;

/**
 * One entry in a task's subtask list. Persisted opaquely as a JSONB array on
 * tasks.sub_tasks. The id is a client-generated key (e.g. Date.now()), not a
 * database identity, so it is kept as-is on round-trips.
 */
public record SubtaskItem(
        Long id,
        String title,
        Boolean completed
) {}
