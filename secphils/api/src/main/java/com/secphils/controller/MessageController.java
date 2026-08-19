package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.MessageRequest;
import com.secphils.dto.MessageResponse;
import com.secphils.entity.Message;
import com.secphils.entity.Project;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public MessageController(MessageRepository messageRepository, ProjectRepository projectRepository,
                             UserRepository userRepository,
                              AuditService auditService) {
        this.messageRepository = messageRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageResponse>> list(@RequestParam Long projectId) {
        if (!projectRepository.existsById(projectId)) throw ApiException.notFound("Project");
        return ResponseEntity.ok(
                messageRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                        .map(MessageResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody MessageRequest req,
                                                HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        Message message = new Message();
        message.setProject(project);
        message.setSender(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        message.setBody(req.body());
        message.setCreatedAt(LocalDateTime.now());
        message = messageRepository.save(message);
        auditService.audit(actor, "MESSAGE_SEND", "Message", message.getId(), "Project: " + project.getId(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(message));
    }
}
