package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.SubtaskItem;
import com.secphils.dto.TaskRequest;
import com.secphils.dto.TaskResponse;
import com.secphils.entity.Project;
import com.secphils.entity.Task;
import com.secphils.entity.User;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.TaskRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task endpoints with role-aware ownership:
 *
 * <ul>
 *   <li>ADMIN — "My Tasks" page shows their own tasks by default; pass
 *       {@code scope=ALL} to see every task. Can create, edit and delete any
 *       task, including reassigning the assignee.</li>
 *   <li>USER (non-admin) — sees and manages only tasks assigned to them.
 *       May create tasks (assignee defaults to themselves) and change
 *       status/priority/subtasks/description/due-date, but may not reassign
 *       a task to another user or delete it (403).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaskController(TaskRepository taskRepository, ProjectRepository projectRepository,
                          UserRepository userRepository, AuditService auditService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<TaskResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String scope) {
        AuthUser actor = CurrentUser.require();

        // Scoping: non-admins always see only their own tasks; admins see their
        // own by default, or everything with scope=ALL. An explicit assigneeId
        // filter (admin inspecting someone else's tasks) wins over both.
        Long effectiveAssignee = assigneeId;
        if (effectiveAssignee == null) {
            boolean adminSeesAll = actor.isAdmin() && "ALL".equalsIgnoreCase(scope);
            if (!adminSeesAll) {
                effectiveAssignee = actor.id();
            }
        }

        List<Task> tasks;
        if (effectiveAssignee != null) {
            tasks = taskRepository.findByAssigneeId(effectiveAssignee);
            if (projectId != null && status != null) {
                tasks = taskRepository.findByProjectIdAndAssigneeId(projectId, effectiveAssignee)
                        .stream().filter(t -> status.equalsIgnoreCase(t.getStatus())).toList();
            } else if (projectId != null) {
                tasks = taskRepository.findByProjectIdAndAssigneeId(projectId, effectiveAssignee);
            }
        } else if (projectId != null && status != null) {
            tasks = taskRepository.findByProjectIdAndStatus(projectId, status);
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectId(projectId);
        } else if (status != null) {
            tasks = taskRepository.findByStatus(status);
        } else {
            tasks = taskRepository.findAll();
        }
        if (priority != null && !priority.isBlank()) {
            tasks = tasks.stream().filter(t -> priority.equalsIgnoreCase(t.getPriority())).toList();
        }
        return ResponseEntity.ok(tasks.stream().map(TaskResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest req,
                                               HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        // Non-admin creators own their tasks: unassigned defaults to themselves,
        // someone else's id is rejected. Admins may assign anyone (or none).
        if (!actor.isAdmin()) {
            if (req.assigneeId() == null) {
                req = new TaskRequest(req.projectId(), actor.id(), req.title(), req.description(),
                        req.status(), req.priority(), req.dueDate(), req.subtasks());
            } else if (req.assigneeId() != actor.id()) {
                throw ApiException.forbidden("You can only create tasks for yourself");
            }
        }
        Task task = new Task();
        apply(task, req);
        task.setCreatedAt(LocalDateTime.now());
        task = taskRepository.save(task);
        auditService.audit(actor, "TASK_CREATE", "Task", task.getId(), "Title: " + task.getTitle(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(task));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<TaskResponse> get(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Task task = taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
        assertReadable(task, actor);
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<TaskResponse> update(@PathVariable Long id,
                                               @RequestBody TaskRequest req,
                                               HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Task task = taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
        assertWritable(task, actor, req);
        applyPartial(task, req);
        task = taskRepository.save(task);
        auditService.audit(actor, "TASK_UPDATE", "Task", task.getId(), "Title: " + task.getTitle(), http);
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<TaskResponse> updateStatus(@PathVariable Long id,
                                                     @RequestBody TaskRequest req,
                                                     HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Task task = taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
        if (!actor.isAdmin()) {
            boolean isOwn = task.getAssignee() != null && actor.id().equals(task.getAssignee().getId());
            if (!isOwn) {
                throw ApiException.forbidden("You can only update your own tasks");
            }
        }
        if (req.status() != null && !req.status().isBlank()) {
            task.setStatus(req.status());
        }
        if (req.priority() != null && !req.priority().isBlank()) {
            task.setPriority(req.priority());
        }
        task = taskRepository.save(task);
        auditService.audit(actor, "TASK_STATUS_CHANGE", "Task", task.getId(),
                "Status: " + task.getStatus(), http);
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Task task = taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
        assertReadable(task, actor);
        // Only admins delete tasks — a USER can un-assign by editing, but not
        // erase the record (audit integrity).
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Only admins can delete tasks");
        }
        taskRepository.delete(task);
        auditService.audit(actor, "TASK_DELETE", "Task", id, "Title: " + task.getTitle(), http);
        return ResponseEntity.noContent().build();
    }

    // ---------- ownership checks ----------

    private void assertReadable(Task task, AuthUser actor) {
        if (actor.isAdmin()) return;
        if (task.getAssignee() == null || !actor.id().equals(task.getAssignee().getId())) {
            // Non-admins cannot read other people's tasks at all.
            throw ApiException.notFound("Task");
        }
    }

    private void assertWritable(Task task, AuthUser actor, TaskRequest req) {
        if (actor.isAdmin()) return;
        if (task.getAssignee() == null || !actor.id().equals(task.getAssignee().getId())) {
            throw ApiException.forbidden("You can only edit your own tasks");
        }
        // Non-admins may not reassign the task to someone else (or unassign it).
        if (req.assigneeId() != null && !req.assigneeId().equals(actor.id())) {
            throw ApiException.forbidden("You can only reassign tasks to yourself");
        }
    }

    // ---------- field application ----------

    private void apply(Task task, TaskRequest req) {
        Project project = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        task.setProject(project);
        if (req.assigneeId() != null) {
            User assignee = userRepository.findById(req.assigneeId())
                    .orElseThrow(() -> ApiException.notFound("User"));
            task.setAssignee(assignee);
        }
        task.setTitle(req.title());
        task.setDescription(req.description());
        if (req.status() != null && !req.status().isBlank()) task.setStatus(req.status());
        if (req.priority() != null && !req.priority().isBlank()) task.setPriority(req.priority());
        task.setDueDate(req.dueDate());
        task.setSubtasks(serializeSubtasks(req.subtasks()));
    }

    private void applyPartial(Task task, TaskRequest req) {
        if (req.projectId() != null) {
            Project project = projectRepository.findById(req.projectId())
                    .orElseThrow(() -> ApiException.notFound("Project"));
            task.setProject(project);
        }
        if (req.assigneeId() != null) {
            User assignee = userRepository.findById(req.assigneeId())
                    .orElseThrow(() -> ApiException.notFound("User"));
            task.setAssignee(assignee);
        }
        if (req.title() != null && !req.title().isBlank()) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.status() != null && !req.status().isBlank()) task.setStatus(req.status());
        if (req.priority() != null && !req.priority().isBlank()) task.setPriority(req.priority());
        if (req.dueDate() != null) task.setDueDate(req.dueDate());
        // Explicit subtask array (including empty) replaces the stored list;
        // a null field leaves existing subtasks untouched.
        if (req.subtasks() != null) {
            task.setSubtasks(serializeSubtasks(req.subtasks()));
        }
    }

    /**
     * Normalize a subtask list to a JSON array string for the JSONB column:
     * null becomes "[]", blank titles are dropped, and items are re-serialized
     * verbatim (id/title/completed).
     */
    private static String serializeSubtasks(List<SubtaskItem> subtasks) {
        if (subtasks == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (SubtaskItem s : subtasks) {
            if (s == null) continue;
            String title = s.title() == null ? "" : s.title().trim();
            if (title.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(s.id() == null ? 0 : s.id())
              .append(",\"title\":").append(jsonEscape(title))
              .append(",\"completed\":").append(Boolean.TRUE.equals(s.completed()) ? "true" : "false")
              .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
