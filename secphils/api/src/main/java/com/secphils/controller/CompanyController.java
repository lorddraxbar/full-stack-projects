package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.*;
import com.secphils.entity.Company;
import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.MailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MailService mailService;
    private final SystemSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final String inviteBaseUrl;
    private final Duration inviteTtl;

    public CompanyController(CompanyRepository companyRepository, UserRepository userRepository,
                             AuditService auditService, MailService mailService,
                             SystemSettingsRepository settingsRepository, PasswordEncoder passwordEncoder,
                             @Value("${app.invite.base-url}") String inviteBaseUrl,
                             @Value("${app.invite.token-ttl:24h}") Duration inviteTtl) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.mailService = mailService;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.inviteBaseUrl = inviteBaseUrl;
        this.inviteTtl = inviteTtl;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CompanyResponse>> list() {
        return ResponseEntity.ok(companyRepository.findAll().stream().map(CompanyResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Company company = new Company();
        apply(company, req);
        if (req.authorizedRepId() != null) {
            User rep = userRepository.findById(req.authorizedRepId())
                    .orElseThrow(() -> ApiException.notFound("Authorized representative user"));
            company.setAuthorizedRep(rep);
        }
        company = companyRepository.save(company);
        auditService.audit(actor, "COMPANY_CREATE", "Company", company.getId(), "Name: " + company.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompanyResponse.from(company));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<CompanyResponse> get(@PathVariable Long id) {
        Company company = companyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Company"));
        return ResponseEntity.ok(CompanyResponse.from(company));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<CompanyResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody CompanyRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Company company = companyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Company"));
        apply(company, req);
        if (req.authorizedRepId() != null) {
            User rep = userRepository.findById(req.authorizedRepId())
                    .orElseThrow(() -> ApiException.notFound("Authorized representative user"));
            company.setAuthorizedRep(rep);
        }
        company = companyRepository.save(company);
        auditService.audit(actor, "COMPANY_UPDATE", "Company", company.getId(), "Name: " + company.getName(), http);
        return ResponseEntity.ok(CompanyResponse.from(company));
    }

    // ---- Client Settings: own company ----

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<CompanyResponse> myCompany() {
        Company company = ownCompany(CurrentUser.require());
        return ResponseEntity.ok(CompanyResponse.from(company));
    }

    @PutMapping("/me")
    @Transactional
    public ResponseEntity<CompanyResponse> updateMyCompany(@Valid @RequestBody CompanyRequest req,
                                                           HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        Company company = ownCompany(me);
        applyClientVisible(company, req);
        company = companyRepository.save(company);
        auditService.audit(me, "COMPANY_UPDATE_SELF", "Company", company.getId(),
                "Name: " + company.getName(), http);
        return ResponseEntity.ok(CompanyResponse.from(company));
    }

    @GetMapping("/me/team")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CompanyTeamMemberResponse>> myTeam() {
        AuthUser me = CurrentUser.require();
        Long companyId = userRepository.findById(me.id()).map(User::getCompanyId).orElse(null);
        if (companyId == null) {
            throw ApiException.badRequest("You are not linked to a company");
        }
        List<CompanyTeamMemberResponse> team = userRepository.findAll().stream()
                .filter(u -> companyId.equals(u.getCompanyId()))
                .map(CompanyTeamMemberResponse::from)
                .toList();
        return ResponseEntity.ok(team);
    }

    @PostMapping("/me/team/invite")
    @Transactional
    public ResponseEntity<Map<String, Object>> inviteTeamMember(@Valid @RequestBody TeamInviteRequest req,
                                                                HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        User actor = userRepository.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (actor.getCompanyId() == null) {
            throw ApiException.badRequest("You are not linked to a company");
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw ApiException.conflict("A user with this email already exists");
        }
        Company company = companyRepository.findById(actor.getCompanyId())
                .orElseThrow(() -> ApiException.notFound("Company"));

        User invitee = new User();
        invitee.setEmail(req.email());
        invitee.setFirstName(fullName(req.name()));
        invitee.setLastName("");
        invitee.setRole(req.role() != null && !req.role().isBlank() ? req.role() : "CLIENT");
        invitee.setCompanyId(company.getId());
        invitee.setIsActive(false);
        invitee = userRepository.save(invitee);

        String token = newToken();
        invitee.setPasswordResetToken(token);
        invitee.setPasswordResetExpiresAt(LocalDateTime.now().plus(inviteTtl));
        invitee.setPasswordResetRequestedAt(LocalDateTime.now());
        userRepository.save(invitee);

        String link = resolveInviteBaseUrl(http) + "/auth/set-password?token=" + token;
        mailService.sendHtml(invitee.getEmail(), "Your SECPhils Portal access is ready",
                mailService.inviteEmail(invitee.getFirstName(), invitee.getFullName(), link,
                        DisplayNamePolicy.nameFor(actor), company.getName()),
                link);
        auditService.audit(me, "COMPANY_TEAM_INVITE", "User", invitee.getId(),
                "Email: " + invitee.getEmail() + " -> " + company.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", invitee.getId(),
                "name", DisplayNamePolicy.nameFor(invitee),
                "email", invitee.getEmail(),
                "role", invitee.getRole(),
                "status", CompanyTeamMemberResponse.from(invitee).status()));
    }

    private Company ownCompany(AuthUser me) {
        Long companyId = userRepository.findById(me.id()).map(User::getCompanyId).orElse(null);
        if (companyId == null) {
            throw ApiException.forbidden("You are not linked to a company");
        }
        return companyRepository.findById(companyId).orElseThrow(() -> ApiException.notFound("Company"));
    }

    /** Fields the client-facing Company Profile may edit (name, business type, address, contact string). */
    private void applyClientVisible(Company company, CompanyRequest req) {
        company.setName(req.name());
        company.setIndustrySectors(req.industrySectors());
        company.setLocation(req.location());
        company.setContactDetails(req.contactDetails());
    }

    private void apply(Company company, CompanyRequest req) {
        company.setName(req.name());
        company.setLocation(req.location());
        company.setOwner(req.owner());
        company.setDescription(req.description());
        company.setTagline(req.tagline());
        company.setIndustrySectors(req.industrySectors());
        company.setHeadquarters(req.headquarters());
        company.setPhone(req.phone());
        company.setEmail(req.email());
        company.setWebsite(req.website());
        company.setSocialLinks(req.socialLinks());
        company.setTaxNumber(req.taxNumber());
        company.setBankingDetails(req.bankingDetails());
        company.setOperationalFields(req.operationalFields());
        company.setBrandPrimary(req.brandPrimary());
        company.setBrandSecondary(req.brandSecondary());
        company.setLogoUrl(req.logoUrl());
        company.setContactDetails(req.contactDetails());
    }

    private String resolveInviteBaseUrl(HttpServletRequest http) {
        String fromSettings = settingsRepository.findAll().stream().findFirst()
                .map(SystemSettings::getInviteBaseUrl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        if (fromSettings != null) return fromSettings.replaceAll("/+$", "");
        String origin = http.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String referer = http.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    java.net.URI u = java.net.URI.create(referer);
                    if (u.getScheme() != null && u.getAuthority() != null) {
                        origin = u.getScheme() + "://" + u.getAuthority();
                    }
                } catch (Exception ignored) {
                    // malformed referer — fall through
                }
            }
        }
        if (origin != null && !origin.isBlank()) return origin.replaceAll("/+$", "");
        return inviteBaseUrl.replaceAll("/+$", "");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String fullName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}