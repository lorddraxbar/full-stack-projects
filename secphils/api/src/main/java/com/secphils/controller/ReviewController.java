package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.ReviewRequest;
import com.secphils.dto.ReviewResponse;
import com.secphils.entity.Project;
import com.secphils.entity.Review;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.ReviewRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public ReviewController(ReviewRepository reviewRepository, ProjectRepository projectRepository,
                            UserRepository userRepository,
                              AuditService auditService) {
        this.reviewRepository = reviewRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        List<Review> items;
        if (projectId != null) {
            items = reviewRepository.findByProjectId(projectId).map(List::of).orElse(List.of());
        } else if (status != null && !status.isBlank()) {
            items = reviewRepository.findByStatus(status);
        } else {
            items = reviewRepository.findAll();
        }
        return ResponseEntity.ok(items.stream().map(ReviewResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest req,
                                                 HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        reviewRepository.findByProjectId(project.getId())
                .ifPresent(existing -> {
                    throw ApiException.conflict("A review already exists for this project");
                });
        Review review = new Review();
        review.setCustomerUser(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        review.setProject(project);
        review.setRating(req.rating());
        review.setTitle(req.title());
        review.setBody(req.body());
        review.setCreatedAt(LocalDateTime.now());
        review = reviewRepository.save(review);
        auditService.audit(actor, "REVIEW_CREATE", "Review", review.getId(), "Project: " + project.getId(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(review));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ReviewResponse> get(@PathVariable Long id) {
        Review review = reviewRepository.findById(id).orElseThrow(() -> ApiException.notFound("Review"));
        return ResponseEntity.ok(ReviewResponse.from(review));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<ReviewResponse> updateStatus(@PathVariable Long id,
                                                       @RequestBody Map<String, String> body,
                                                       HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Review review = reviewRepository.findById(id).orElseThrow(() -> ApiException.notFound("Review"));
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw ApiException.badRequest("status is required");
        }
        review.setStatus(status);
        review = reviewRepository.save(review);
        auditService.audit(actor, "REVIEW_STATUS_CHANGE", "Review", review.getId(), "Status: " + status, http);
        return ResponseEntity.ok(ReviewResponse.from(review));
    }
}
