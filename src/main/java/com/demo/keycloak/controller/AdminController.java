package com.demo.keycloak.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")   // all methods in this controller require ADMIN
public class AdminController {

    @GetMapping("/dashboard")
    public Map<String, Object> adminDashboard(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Map.of(
            "message", "Admin Dashboard — restricted access",
            "admin", jwt.getClaimAsString("preferred_username"),
            "tokenId", jwt.getId() != null ? jwt.getId() : "N/A"
        );
    }

    @GetMapping("/users")
    public Map<String, Object> listUsers() {
        // In a real app, call Keycloak Admin REST API here
        return Map.of(
            "message", "List of users would come from Keycloak Admin API",
            "hint", "Use KeycloakAdminClient or REST: GET /admin/realms/{realm}/users"
        );
    }

    @GetMapping("/jwt-claims")
    public Object allClaims(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        // Exposes all JWT claims — useful for debugging
        return jwt.getClaims();
    }
}
