package com.demo.keycloak.controller;

import com.demo.keycloak.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagePublisher publisher;

    /**
     * Publish a custom event for the authenticated user.
     * Example: POST /api/messages/send  {"action": "custom-action"}
     */
    @PostMapping("/send")
    public Map<String, String> sendMessage(@RequestBody Map<String, String> body,
                                           Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        String action = body.getOrDefault("action", "manual-trigger");

        publisher.publishUserEvent(username, action);
        return Map.of("status", "sent", "user", username, "action", action);
    }
}
