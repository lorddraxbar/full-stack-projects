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
import com.secphils.service.RepChangeNotificationService;
import com.secphils.service.TeamRemovalNotificationService;
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
    private final RepChangeNotificationService repChangeNotifications;
    private final TeamRemovalNotificationService teamRemovalNotifications;
    private final String inviteBaseUrl;
    private final Duration inviteTtl;

    public CompanyController(CompanyRepository companyRepository, UserRepository userRepository,
                             AuditService auditService, MailService mailService,
                             SystemSettingsRepository settingsRepository, PasswordEncoder passwordEncoder,
                             RepChangeNotificationService repChangeNotifications,
                             TeamRemovalNotificationService teamRemovalNotifications,
                             @Value("${app.invite.base-url}") String inviteBaseUrl,
                             @Value("${app.invite.token-ttl:24h}") Duration inviteTtl) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.mailService = mailService;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.repChangeNotifications = repChangeNotifications;
        this.teamRemovalNotifications = teamRemovalNotifications;
        this.inviteBaseUrl = inviteBaseUrl;
        this.inviteTtl = inviteTtl;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CompanyResponse>> list() {
        return ResponseEntity.ok(companyRepository.findWithRep().stream().map(CompanyResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        User actorUser = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        Company company = new Company();
        apply(company, req);
        User rep = null;
        if (req.authorizedRepId() != null) {
            rep = userRepository.findById(req.authorizedRepId())
                    .orElseThrow(() -> ApiException.notFound("Authorized representative user"));
        } else if (req.repName() != null && !req.repName().isBlank()
                && req.email() != null && !req.email().isBlank()) {
            // New-customer wizard: the rep doesn't exist yet — create their
            // (inactive) CLIENT account, invite them, and make them the rep.
            if (userRepository.findByEmail(req.email()).isPresent()) {
                throw ApiException.conflict("A user with this email already exists");
            }
            rep = new User();
            rep.setEmail(req.email());
            String[] repName = splitName(req.repName());
            rep.setFirstName(repName[0]);
            rep.setLastName(repName[1]);
            if (req.repPhone() != null && !req.repPhone().isBlank()) rep.setPhone(req.repPhone());
            rep.setRole("CLIENT");
            rep.setIsActive(false);
            rep = userRepository.save(rep);
        }
        if (rep != null) {
            // Link before the company is saved so the FK column is written on
            // the company insert (the rep row already has its own id).
            company.setAuthorizedRep(rep);
        }
        company = companyRepository.save(company);
        if (rep != null) {
            // Now that the company has an id, back-link the rep row and keep
            // it in sync with the FK on the companies table — but only if the
            // rep isn't already a member of a different company (never yank
            // an existing user off their company; the fresh-rep path above
            // always has a null company and is the one the wizard uses).
            if (rep.getCompanyId() == null) {
                rep.setCompanyId(company.getId());
                userRepository.save(rep);
            }
        }
        auditService.audit(actor, "COMPANY_CREATE", "Company", company.getId(), "Name: " + company.getName(), http);
        if (rep != null && rep.getPasswordResetToken() == null && !rep.getIsActive()) {
            // Brand-new rep account: mint the password-set token and send the
            // onboarding invite (same flow as the existing-customer team invite).
            sendInvite(actor, actorUser, rep, company, http, "COMPANY_CUSTOMER_REP_INVITE");
        }
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
        Long previousRepId = company.getAuthorizedRep() == null ? null : company.getAuthorizedRep().getId();
        apply(company, req);
        if (req.authorizedRepId() != null) {
            User rep = userRepository.findById(req.authorizedRepId())
                    .orElseThrow(() -> ApiException.notFound("Authorized representative user"));
            // Fill in the rep's phone when the wizard provided one and the user
            // row is blank — never clobber a phone the rep set for themselves.
            if (req.repPhone() != null && !req.repPhone().isBlank()
                    && (rep.getPhone() == null || rep.getPhone().isBlank())) {
                rep.setPhone(req.repPhone());
                userRepository.save(rep);
            }
            company.setAuthorizedRep(rep);
        }
        company = companyRepository.save(company);
        auditService.audit(actor, "COMPANY_UPDATE", "Company", company.getId(), "Name: " + company.getName(), http);
        // The wizard's existing-customer path moves the rep through this PUT —
        // announce a REAL change (re-selecting the current rep stays silent).
        Long newRepId = company.getAuthorizedRep() == null ? null : company.getAuthorizedRep().getId();
        if (newRepId != null && !newRepId.equals(previousRepId)) {
            repChangeNotifications.onRepChanged(company, previousRepId, company.getAuthorizedRep(), actor.id());
        }
        return ResponseEntity.ok(CompanyResponse.from(company));
    }

    // ---- Client Settings: own company ----

    /**
     * Staff sets (or re-sets) a company's authorized representative from that
     * company's CLIENT team — the same pick/add-new UX the New Project wizard
     * uses (GET /{id}/team to pick, POST /{id}/team/invite to add). Scoped so
     * the caller never has to PUT the whole company or touch the user row
     * directly. Contact fields are optional and applied to the rep's User row:
     * blank leaves it untouched; email only changes when it differs and
     * collides with nobody.
     */
    @PutMapping("/{id}/authorized-rep")
    @Transactional
    public ResponseEntity<CompanyResponse> setAuthorizedRep(@PathVariable Long id,
                                                            @Valid @RequestBody AuthorizedRepRequest req,
                                                            HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (!actor.isUserOrAdmin()) {
            throw ApiException.forbidden("Only provider staff can change the authorized representative");
        }
        Company company = companyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Company"));
        User rep = userRepository.findById(req.repUserId())
                .orElseThrow(() -> ApiException.notFound("Selected user"));
        // The rep must be a client of THIS company — mirrors the team picker's
        // set and blocks pointing the rep at a provider or foreign account.
        if (rep.getCompanyId() == null || !rep.getCompanyId().equals(company.getId())) {
            throw ApiException.badRequest("The selected user is not a client of this company");
        }
        if (rep.getRole() == null || !rep.getRole().trim().equalsIgnoreCase("CLIENT")) {
            throw ApiException.badRequest("The authorized representative must be a client account of this company");
        }
        boolean touched = false;
        if (req.fullName() != null && !req.fullName().isBlank()) {
            String[] parts = splitName(req.fullName().trim());
            rep.setFirstName(parts[0]);
            rep.setLastName(parts[1]);
            touched = true;
        }
        if (req.email() != null && !req.email().isBlank()
                && !req.email().trim().equalsIgnoreCase(rep.getEmail())) {
            if (userRepository.findByEmail(req.email().trim()).isPresent()) {
                throw ApiException.conflict("A user with this email already exists");
            }
            rep.setEmail(req.email().trim());
            touched = true;
        }
        if (req.phone() != null && !req.phone().isBlank()
                && !req.phone().trim().equals(rep.getPhone())) {
            rep.setPhone(req.phone().trim());
            touched = true;
        }
        if (touched) userRepository.save(rep);
        Long previousRepId = company.getAuthorizedRep() == null ? null : company.getAuthorizedRep().getId();
        company.setAuthorizedRep(rep);
        company = companyRepository.save(company);
        auditService.audit(actor, "COMPANY_REP_SET", "Company", company.getId(),
                "Rep: " + rep.getEmail(), http);
        // Same announcement the wizard path gets (re-selecting the current rep
        // stays silent): new rep + released old rep + provider staff.
        if (!rep.getId().equals(previousRepId)) {
            repChangeNotifications.onRepChanged(company, previousRepId, rep, actor.id());
        }
        return ResponseEntity.ok(CompanyResponse.from(company));
    }

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
                // SECURITY: mirror the staff-side /{id}/team filter — a client's
                // team view shows only the company's CLIENT accounts. Provider
                // staff may share a companyId (the provider company) and must
                // never surface in a client's Team & Invitations list.
                .filter(u -> u.getRole() != null && u.getRole().trim().equalsIgnoreCase("CLIENT"))
                .map(CompanyTeamMemberResponse::from)
                .toList();
        return ResponseEntity.ok(team);
    }

    /**
     * The company's AUTHORIZED REPRESENTATIVE removes a teammate's portal
     * access. This is a DEACTIVATION, never a hard delete — the same honest
     * semantics admins use: login is blocked immediately (JwtAuthFilter
     * re-checks active state per request), history stays attributed, and the
     * member is restorable by a provider admin within the retention window.
     * Rep-only by policy: the rep owns the company's access. The rep cannot
     * remove themselves and cannot remove the rep (integrity wall — the
     * companies.authorized_rep FK stays RESTRICT).
     */
    @PostMapping("/me/team/{userId}/deactivate")
    @Transactional
    public ResponseEntity<Void> deactivateTeamMember(@PathVariable Long userId, HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        User actor = userRepository.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
        Company company = ownCompany(me);
        if (company.getAuthorizedRep() == null || !company.getAuthorizedRep().getId().equals(actor.getId())) {
            throw ApiException.forbidden("Only the authorized representative can remove team members");
        }
        if (actor.getId().equals(userId)) {
            throw ApiException.badRequest("You cannot remove your own access");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Team member"));
        // The target must be a CLIENT of THIS company — never a provider account,
        // never another company's member.
        if (target.getCompanyId() == null || !target.getCompanyId().equals(company.getId())) {
            throw ApiException.notFound("Team member");
        }
        if (target.getRole() == null || !target.getRole().trim().equalsIgnoreCase("CLIENT")) {
            throw ApiException.forbidden("Provider accounts cannot be removed here");
        }
        if (company.getAuthorizedRep().getId().equals(target.getId())) {
            throw ApiException.conflict("The authorized representative cannot be removed. Contact SECPhils to change the representative first.");
        }
        if (!Boolean.TRUE.equals(target.getIsActive())) {
            throw ApiException.conflict("This member's access is already removed");
        }
        target.setIsActive(false);
        target.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(target);
        auditService.audit(me, "COMPANY_TEAM_MEMBER_REMOVED", "Company", company.getId(),
                "Removed: " + target.getEmail() + " (deactivated by authorized rep)", http);
        teamRemovalNotifications.onMemberRemoved(company, target, actor, actor.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Portal team of a customer company, browsable by staff/admin (the client-facing
     * counterpart is GET /companies/me/team, which is locked to the caller's own company).
     * Staff see real names — the brand-collapsing client policy does not apply here.
     */
    @GetMapping("/{id}/team")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CompanyTeamMemberResponse>> team(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        if (!actor.isUserOrAdmin()) {
            throw ApiException.forbidden("Only staff can browse customer teams");
        }
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Company"));
        // Only CLIENT-role users are valid authorized reps for a customer company.
        // Real production customer companies contain just CLIENT users, so this is a
        // no-op there — it guards against provider (ADMIN/USER) accounts that have been
        // (mis)assigned a customer companyId, which must not become that company's rep.
        List<CompanyTeamMemberResponse> team = userRepository
                .findByCompanyIdOrderByEmail(company.getId())
                .stream()
                .filter(u -> u.getRole() != null && u.getRole().trim().equalsIgnoreCase("CLIENT"))
                .map(CompanyTeamMemberResponse::fromStaff)
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
        String[] inviteeName = splitName(req.name());
        invitee.setFirstName(inviteeName[0]);
        invitee.setLastName(inviteeName[1]);
        if (req.phone() != null && !req.phone().isBlank()) invitee.setPhone(req.phone());
        // SECURITY: team members of a customer company are ALWAYS CLIENTs.
        // The caller here is themselves a CLIENT (client self-service invite) —
        // honouring a caller-supplied role let any client mint an ADMIN/USER
        // account via the invite link (privilege escalation, verified live
        // 2026-09-07). The old `role` request field is ignored; the DTO no
        // longer declares it.
        invitee.setRole("CLIENT");
        invitee.setCompanyId(company.getId());
        invitee.setIsActive(false);
        invitee = userRepository.save(invitee);

        sendInvite(me, actor, invitee, company, http, "COMPANY_TEAM_INVITE");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", invitee.getId(),
                "name", DisplayNamePolicy.nameFor(invitee),
                "email", invitee.getEmail(),
                "role", invitee.getRole(),
                "status", CompanyTeamMemberResponse.from(invitee).status()));
    }

    /**
     * Staff/admin invites a NEW client user to a customer company from the project wizard.
     * The invitee is created as an inactive CLIENT on that company and, when setAsRep, is
     * also made the company's authorized representative (the onboarding reviewer).
     */
    @PostMapping("/{id}/team/invite")
    @Transactional
    public ResponseEntity<Map<String, Object>> addCustomerRep(@Valid @RequestBody CustomerRepInviteRequest req,
                                                              @PathVariable Long id,
                                                              HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        User actorUser = userRepository.findById(actor.id()).orElseThrow(() -> ApiException.notFound("User"));
        Company company = companyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Company"));
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw ApiException.conflict("A user with this email already exists");
        }
        User invitee = new User();
        invitee.setEmail(req.email());
        String[] inviteeName = splitName(req.name());
        invitee.setFirstName(inviteeName[0]);
        invitee.setLastName(inviteeName[1]);
        if (req.phone() != null && !req.phone().isBlank()) invitee.setPhone(req.phone());
        // The onboarding reviewer must be a client of this company — never a provider account.
        invitee.setRole("CLIENT");
        invitee.setCompanyId(company.getId());
        invitee.setIsActive(false);
        invitee = userRepository.save(invitee);
        sendInvite(actor, actorUser, invitee, company, http, "COMPANY_CUSTOMER_REP_INVITE");
        if (req.setAsRep()) {
            company.setAuthorizedRep(invitee);
            companyRepository.save(company);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", invitee.getId(),
                "name", CompanyTeamMemberResponse.fromStaff(invitee).name(),
                "email", invitee.getEmail(),
                "role", invitee.getRole(),
                "status", CompanyTeamMemberResponse.from(invitee).status()));
    }

    /** Create the one-time password-reset token and send the branded invite email. */
    private void sendInvite(AuthUser actor, User actorUser, User invitee, Company company,
                            HttpServletRequest http, String auditAction) {
        String token = newToken();
        invitee.setPasswordResetToken(token);
        invitee.setPasswordResetExpiresAt(LocalDateTime.now().plus(inviteTtl));
        invitee.setPasswordResetRequestedAt(LocalDateTime.now());
        userRepository.save(invitee);
        String link = resolveInviteBaseUrl(http) + "/auth/set-password?token=" + token;
        mailService.sendHtml(invitee.getEmail(), mailService.inviteSubject(),
                mailService.inviteEmail(invitee.getFirstName(), invitee.getFullName(), link,
                        DisplayNamePolicy.nameFor(actorUser), company.getName()),
                link);
        auditService.audit(actor, auditAction, "User", invitee.getId(),
                "Email: " + invitee.getEmail() + " -> " + company.getName(), http);
    }

    private Company ownCompany(AuthUser me) {
        Long companyId = userRepository.findById(me.id()).map(User::getCompanyId).orElse(null);
        if (companyId == null) {
            throw ApiException.forbidden("You are not linked to a company");
        }
        return companyRepository.findById(companyId).orElseThrow(() -> ApiException.notFound("Company"));
    }

    /** Fields the client-facing Company Profile may edit (name, business type,
     *  owner + owner phone, address, contact string). */
    private void applyClientVisible(Company company, CompanyRequest req) {
        company.setName(req.name());
        company.setIndustrySectors(req.industrySectors());
        company.setOwner(req.owner());
        company.setOwnerPhone(req.ownerPhone());
        company.setLocation(req.location());
        company.setContactDetails(req.contactDetails());
    }

    /**
     * Null-safe field application: sparse updates (e.g. the project wizard bumping only
     * the authorized rep) must not clobber fields the caller did not send.
     */
    private void apply(Company company, CompanyRequest req) {
        if (req.name() != null) company.setName(req.name());
        if (req.location() != null) company.setLocation(req.location());
        if (req.owner() != null) company.setOwner(req.owner());
        if (req.ownerPhone() != null) company.setOwnerPhone(req.ownerPhone());
        if (req.description() != null) company.setDescription(req.description());
        if (req.about() != null) company.setAbout(req.about());
        if (req.tagline() != null) company.setTagline(req.tagline());
        if (req.industrySectors() != null) company.setIndustrySectors(req.industrySectors());
        if (req.headquarters() != null) company.setHeadquarters(req.headquarters());
        if (req.phone() != null) company.setPhone(req.phone());
        if (req.email() != null) company.setEmail(req.email());
        if (req.website() != null) company.setWebsite(req.website());
        if (req.socialLinks() != null) company.setSocialLinks(req.socialLinks());
        if (req.taxNumber() != null) company.setTaxNumber(req.taxNumber());
        if (req.bankingDetails() != null) company.setBankingDetails(req.bankingDetails());
        if (req.operationalFields() != null) company.setOperationalFields(req.operationalFields());
        if (req.brandPrimary() != null) company.setBrandPrimary(req.brandPrimary());
        if (req.brandSecondary() != null) company.setBrandSecondary(req.brandSecondary());
        if (req.logoUrl() != null) company.setLogoUrl(req.logoUrl());
        if (req.contactDetails() != null) company.setContactDetails(req.contactDetails());
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

    /**
     * Splits a free-form full name into first/last: the first token becomes the
     * first name and everything after it the last name, so multi-word names are
     * preserved by getFullName() instead of being truncated to the first word.
     */
    private static String[] splitName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.indexOf(' ');
        return new String[]{
                space > 0 ? trimmed.substring(0, space) : trimmed,
                space > 0 ? trimmed.substring(space + 1) : ""
        };
    }
}