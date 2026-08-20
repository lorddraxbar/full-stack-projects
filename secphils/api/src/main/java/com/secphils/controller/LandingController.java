package com.secphils.controller;

import com.secphils.entity.Company;
import com.secphils.entity.Review;
import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.ReviewRepository;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public landing-page data (no auth). Serves the portal name, the provider's
 * company profile (Admin Panel > Company Settings > Company Profile) and the
 * approved client reviews, so the marketing landing page can be populated
 * entirely from the admin-managed profile.
 */
@RestController
@RequestMapping("/api/v1/landing")
public class LandingController {

    private final SystemSettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public LandingController(SystemSettingsRepository settingsRepository,
                             CompanyRepository companyRepository,
                             ReviewRepository reviewRepository,
                             UserRepository userRepository) {
        this.settingsRepository = settingsRepository;
        this.companyRepository = companyRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> landing() {
        SystemSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElse(new SystemSettings());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("portalName", settings.getPortalName() != null ? settings.getPortalName() : "SECPhils");
        payload.put("tagline", "Engineering excellence, delivered.");
        payload.put("maintenanceMode", Boolean.TRUE.equals(settings.getMaintenanceMode()));
        payload.put("company", companyProfile());
        payload.put("reviews", approvedReviews());
        return ResponseEntity.ok(payload);
    }

    /**
     * Resolve the provider's company: the company an ADMIN user belongs to,
     * falling back to the first existing company record.
     */
    private Map<String, Object> companyProfile() {
        Company company = null;
        Long companyId = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .map(User::getCompanyId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (companyId != null) {
            company = companyRepository.findById(companyId).orElse(null);
        }
        if (company == null) {
            company = companyRepository.findAll().stream().findFirst().orElse(null);
        }
        if (company == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", company.getId());
        m.put("name", company.getName());
        m.put("location", company.getLocation());
        m.put("owner", company.getOwner());
        m.put("description", company.getDescription());
        m.put("tagline", company.getTagline());
        m.put("industrySectors", company.getIndustrySectors());
        m.put("headquarters", company.getHeadquarters());
        m.put("phone", company.getPhone());
        m.put("email", company.getEmail());
        m.put("website", company.getWebsite());
        m.put("socialLinks", company.getSocialLinks());
        m.put("brandPrimary", company.getBrandPrimary());
        m.put("brandSecondary", company.getBrandSecondary());
        m.put("logoUrl", company.getLogoUrl());
        return m;
    }

    private List<Map<String, Object>> approvedReviews() {
        return reviewRepository.findByStatus("Approved").stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("customerName", r.getCustomerUser() != null ? r.getCustomerUser().getFullName() : null);
                    m.put("projectName", r.getProject() != null ? r.getProject().getName() : null);
                    m.put("rating", r.getRating());
                    m.put("title", r.getTitle());
                    m.put("body", r.getBody());
                    m.put("createdAt", r.getCreatedAt());
                    return m;
                }).toList();
    }
}
