package com.demo.keycloak.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
            "message", "Hello from public endpoint — no authentication required!",
            "status", "OK"
        );
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
            "app", "Keycloak Spring Demo",
            "description", "Protected API with Keycloak JWT auth"
        );
    }
}
