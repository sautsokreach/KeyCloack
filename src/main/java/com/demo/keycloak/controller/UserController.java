package com.demo.keycloak.controller;

import com.demo.keycloak.dto.UserInfoDto;
import com.demo.keycloak.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final MessagePublisher publisher;

    @GetMapping("/profile")
    public UserInfoDto getProfile(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();

        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        publisher.publishUserEvent(jwt.getClaimAsString("preferred_username"), "profile-viewed");

        return UserInfoDto.builder()
            .username(jwt.getClaimAsString("preferred_username"))
            .email(jwt.getClaimAsString("email"))
            .subject(jwt.getSubject())
            .roles(roles)
            .issuer(jwt.getIssuer().toString())
            .build();
    }

    /**
     * Demonstrates @PreAuthorize — alternative to SecurityConfig rules.
     * hasRole() automatically prepends "ROLE_" prefix.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Object dashboard(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");

        return new Object() {
            public final String message = "Welcome to your dashboard, " + username + "!";
            public final String tokenExpiry = jwt.getExpiresAt() != null
                ? jwt.getExpiresAt().toString() : "N/A";
        };
    }
}
