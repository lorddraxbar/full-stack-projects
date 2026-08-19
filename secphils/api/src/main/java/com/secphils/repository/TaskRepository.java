package com.secphils.repository;

import com.secphils.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);

    List<Task> findByAssigneeId(Long assigneeId);

    List<Task> findByProjectIdAndStatus(Long projectId, String status);

    List<Task> findByStatus(String status);

    List<Task> findByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    long countByProjectIdAndStatus(Long projectId, String status);
}
