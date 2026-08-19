package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

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
            @RequestParam(required = false) Long assigneeId) {
        List<Task> tasks;
        if (projectId != null && status != null) {
            tasks = taskRepository.findByProjectIdAndStatus(projectId, status);
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectId(projectId);
        } else if (assigneeId != null) {
            tasks = taskRepository.findByAssigneeId(assigneeId);
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
        Task task = taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<TaskResponse> update(@PathVariable Long id,
                                               @RequestBody TaskRequest req,
                                               HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Task task = taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
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
        taskRepository.delete(task);
        auditService.audit(actor, "TASK_DELETE", "Task", id, "Title: " + task.getTitle(), http);
        return ResponseEntity.noContent().build();
    }

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
    }
}
