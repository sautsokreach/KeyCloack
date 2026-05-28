package com.demo.keycloak.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts roles from a Keycloak JWT token and maps them to Spring Security authorities.
 *
 * Keycloak JWT structure:
 *   realm_access.roles              → realm-level roles  (e.g. "USER", "ADMIN")
 *   resource_access.<clientId>.roles → client-level roles
 *
 * Spring Security expects "ROLE_" prefix, so "USER" becomes "ROLE_USER".
 */
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    // Set this to your Keycloak client-id if you want client-level roles too
    private static final String CLIENT_ID = "spring-demo-client";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return Stream.concat(
            extractRealmRoles(jwt).stream(),
            extractClientRoles(jwt).stream()
        ).collect(Collectors.toSet());
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !realmAccess.containsKey(ROLES_CLAIM)) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get(ROLES_CLAIM);
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
            .collect(Collectors.toList());
    }

    private Collection<GrantedAuthority> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null || !resourceAccess.containsKey(CLIENT_ID)) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(CLIENT_ID);
        if (clientAccess == null || !clientAccess.containsKey(ROLES_CLAIM)) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) clientAccess.get(ROLES_CLAIM);
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
            .collect(Collectors.toList());
    }
}
