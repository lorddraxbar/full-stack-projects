package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.CompanyRequest;
import com.secphils.dto.CompanyResponse;
import com.secphils.entity.Company;
import com.secphils.entity.User;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CompanyController(CompanyRepository companyRepository, UserRepository userRepository,
                             AuditService auditService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
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
    }
}
