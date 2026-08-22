package com.secphils.controller;

import com.secphils.dto.LandingContactRequest;
import com.secphils.entity.Company;
import com.secphils.entity.Review;
import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.ReviewRepository;
import com.secphils.repository.ServiceRepository;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import com.secphils.service.MailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public landing-page data (no auth). Serves the portal name, the provider's
 * company profile (Admin Panel > Company Settings > Company Profile) and the
 * approved client reviews, so the marketing landing page can be populated
 * entirely from the admin-managed profile. Also accepts the public contact
 * form and emails it to the company profile's Email Addresses.
 */
@RestController
@RequestMapping("/api/v1/landing")
public class LandingController {

    private final SystemSettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final MailService mailService;

    public LandingController(SystemSettingsRepository settingsRepository,
                             CompanyRepository companyRepository,
                             ReviewRepository reviewRepository,
                             UserRepository userRepository,
                             ServiceRepository serviceRepository,
                             MailService mailService) {
        this.settingsRepository = settingsRepository;
        this.companyRepository = companyRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.mailService = mailService;
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
        payload.put("services", activeServices());
        return ResponseEntity.ok(payload);
    }

    /**
     * Resolve the provider's company: the company an ADMIN user belongs to,
     * falling back to the first existing company record.
     */
    private Company resolveProviderCompany() {
        Long companyId = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .map(User::getCompanyId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (companyId != null) {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company != null) return company;
        }
        return companyRepository.findAll().stream().findFirst().orElse(null);
    }

    private Map<String, Object> companyProfile() {
        Company company = resolveProviderCompany();
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

    /**
     * Public contact form. Emails the submission to every address in the
     * provider's Company Profile "Email Addresses" (comma- or space-separated),
     * falling back to a single profile email, then to a hard default. Mail
     * failures are logged by MailService and never block the response — the
     * form always reports success so a visitor's inquiry is never lost to a
     * transient SMTP outage at the relay.
     */
    @PostMapping("/contact")
    public ResponseEntity<Map<String, Object>> contact(@Valid @RequestBody LandingContactRequest request) {
        Company company = resolveProviderCompany();
        String replyTo = request.email().trim();

        List<String> recipients = new ArrayList<>();
        String raw = company != null ? company.getEmail() : null;
        if (raw != null) {
            for (String part : raw.split("[,;\\s]+")) {
                String p = part.trim();
                if (p.toLowerCase().contains("@")) recipients.add(p);
            }
        }
        if (recipients.isEmpty()) {
            recipients.add("manager@secphils.com");
        }

        String subject = "Landing page inquiry from " + request.firstName().trim() + " " + request.lastName().trim();
        String html = contactEmailHtml(request, replyTo);

        for (String to : recipients) {
            mailService.sendHtml(to, subject, html, null, replyTo);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("recipients", recipients.size());
        return ResponseEntity.ok(out);
    }

    private String contactEmailHtml(LandingContactRequest r, String replyTo) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:32px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                        <tr><td style="background:#29ca8e;padding:24px 32px;">
                          <span style="color:#ffffff;font-size:20px;font-weight:bold;">New Website Inquiry</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="margin:0 0 20px;font-size:14px;line-height:1.6;">
                            Someone submitted the contact form on your website. Details below.
                          </p>
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;">
                            <tr><td style="padding:12px 16px;font-size:14px;"><strong>Full name:</strong> %FULLNAME%</td></tr>
                            <tr><td style="padding:12px 16px;font-size:14px;background:#f9fafb;border-top:1px solid #e5e7eb;"><strong>Email:</strong> %EMAIL%</td></tr>
                            <tr><td style="padding:12px 16px;font-size:14px;border-top:1px solid #e5e7eb;"><strong>Phone:</strong> %PHONE%</td></tr>
                            <tr><td style="padding:12px 16px;font-size:14px;background:#f9fafb;border-top:1px solid #e5e7eb;"><strong>How can we help?</strong><br>%MESSAGE%</td></tr>
                          </table>
                          <p style="margin:20px 0 0;font-size:13px;color:#6b7280;">
                            Reply directly to reach this visitor: <a href="mailto:%EMAIL%" style="color:#29ca8e;">%EMAIL%</a>
                          </p>
                        </td></tr>
                        <tr><td style="padding:16px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;">Received via the SECPhils website contact form.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.replace("%FULLNAME%", escape(r.firstName().trim()) + " " + escape(r.lastName().trim()))
                   .replace("%EMAIL%", escape(replyTo))
                   .replace("%PHONE%", escape(r.phone().trim()))
                   .replace("%MESSAGE%", escape(r.message().trim()).replace("\n", "<br>"));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private List<Map<String, Object>> activeServices() {
        List<com.secphils.entity.Service> active = serviceRepository.findByIsActiveTrue().stream()
                .sorted(java.util.Comparator
                        .comparing((com.secphils.entity.Service s) ->
                                s.getCategory() != null ? s.getCategory().getSortOrder() : Integer.MAX_VALUE)
                        .thenComparing(s -> s.getSortOrder() != null ? s.getSortOrder() : Integer.MAX_VALUE)
                        .thenComparing(com.secphils.entity.Service::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return active.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            m.put("category", s.getCategory() != null ? s.getCategory().getName() : null);
            m.put("categoryIcon", s.getCategory() != null ? s.getCategory().getIcon() : null);
            m.put("icon", s.getIcon());
            m.put("sortOrder", s.getSortOrder());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> approvedReviews() {
        return reviewRepository.findByStatus("APPROVED").stream()
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
