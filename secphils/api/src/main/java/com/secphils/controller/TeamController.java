package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.TeamMemberRequest;
import com.secphils.dto.TeamMemberResponse;
import com.secphils.entity.Project;
import com.secphils.entity.ProjectTeamMember;
import com.secphils.entity.ProjectTeamMemberId;
import com.secphils.entity.User;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.ProjectTeamMemberRepository;
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

@RestController
@RequestMapping("/api/v1/projects/{projectId}/team")
public class TeamController {

    private final ProjectRepository projectRepository;
    private final ProjectTeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TeamController(ProjectRepository projectRepository,
                          ProjectTeamMemberRepository teamMemberRepository,
                          UserRepository userRepository, AuditService auditService) {
        this.projectRepository = projectRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<TeamMemberResponse>> list(@PathVariable Long projectId) {
        requireProject(projectId);
        return ResponseEntity.ok(
                teamMemberRepository.findByProjectId(projectId).stream().map(TeamMemberResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<TeamMemberResponse> add(@PathVariable Long projectId,
                                                  @Valid @RequestBody TeamMemberRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = requireProject(projectId);
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> ApiException.notFound("User"));
        if (teamMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw ApiException.conflict("User is already a team member of this project");
        }
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProject(project);
        member.setUser(user);
        member.setAssignedAt(LocalDateTime.now());
        member = teamMemberRepository.save(member);
        auditService.audit(actor, "TEAM_ADD", "ProjectTeamMember", user.getId(),
                "Project: " + projectId + ", user: " + user.getEmail(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(TeamMemberResponse.from(member));
    }

    @DeleteMapping("/{userId}")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable Long projectId, @PathVariable Long userId,
                                       HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        requireProject(projectId);
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        Project project = projectRepository.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        teamMemberRepository.deleteById(new ProjectTeamMemberId(project, user));
        auditService.audit(actor, "TEAM_REMOVE", "ProjectTeamMember", userId,
                "Project: " + projectId + ", user: " + user.getEmail(), http);
        return ResponseEntity.noContent().build();
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
    }
}
