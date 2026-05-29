package com.demo.keycloak.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    private String username;
    private String action;
    private Instant timestamp;
}
