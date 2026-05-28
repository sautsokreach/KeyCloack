package com.demo.keycloak.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoDto {
    private String username;
    private String email;
    private String subject;          // Keycloak user ID (sub claim)
    private List<String> roles;
    private String issuer;
}
