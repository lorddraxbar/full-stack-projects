package com.secphils.controller;

import com.secphils.entity.SystemSettings;
import com.secphils.repository.SystemSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public landing-page data (no auth). Serves the portal name and a short
 * service blurb for the marketing/landing page.
 */
@RestController
@RequestMapping("/api/v1/landing")
public class LandingController {

    private final SystemSettingsRepository settingsRepository;

    public LandingController(SystemSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> landing() {
        SystemSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElse(new SystemSettings());
        return ResponseEntity.ok(Map.of(
                "portalName", settings.getPortalName() != null ? settings.getPortalName() : "SECPhils",
                "tagline", "Engineering excellence, delivered.",
                "maintenanceMode", Boolean.TRUE.equals(settings.getMaintenanceMode())));
    }
}
