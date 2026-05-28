# Keycloak + Spring Boot — Setup Notes

## Environment

| Item | Value |
|------|-------|
| EC2 Public IP | `52.65.212.193` |
| OS | Ubuntu (resolute) on AWS EC2 |
| Java | OpenJDK 17 |
| Docker | 29.1.3 |
| Docker Compose | v2.40.3 (command: `docker compose`, NOT `docker-compose`) |
| Keycloak | 24.0.4 (via Docker) |
| Spring Boot | 3.2.5 |

---

## Ports

| Port | Service |
|------|---------|
| `8080` | Spring Boot application |
| `8180` | Keycloak server |

> Make sure both ports are open in your **AWS EC2 Security Group** (Inbound rules).

---

## Keycloak

### Admin Console
```
URL      : http://52.65.212.193:8180/admin
Username : admin
Password : admin123
```

### Realm
```
Name : demo-realm
```

### Client
```
Client ID     : spring-demo-client
Client Secret : super-secret-client-key
Grant Types   : authorization_code, password (direct access)
Redirect URIs : http://52.65.212.193:8080/*
               http://localhost:8080/*
```

### Test Users

| Username | Password | Role |
|----------|----------|------|
| `testuser` | `password123` | `USER` |
| `adminuser` | `password123` | `ADMIN` |

### Important URLs

| Purpose | URL |
|---------|-----|
| OIDC Discovery | `http://52.65.212.193:8180/realms/demo-realm/.well-known/openid-configuration` |
| Token endpoint | `http://52.65.212.193:8180/realms/demo-realm/protocol/openid-connect/token` |
| JWK (public keys) | `http://52.65.212.193:8180/realms/demo-realm/protocol/openid-connect/certs` |
| Admin API | `http://52.65.212.193:8180/admin/realms/demo-realm` |

---

## Spring Boot API Endpoints

| Method | Path | Auth Required | Role |
|--------|------|--------------|------|
| GET | `/api/public/hello` | No | — |
| GET | `/api/public/info` | No | — |
| GET | `/api/user/profile` | Yes | `USER` |
| GET | `/api/user/dashboard` | Yes | `USER` or `ADMIN` |
| GET | `/api/admin/dashboard` | Yes | `ADMIN` |
| GET | `/api/admin/users` | Yes | `ADMIN` |
| GET | `/api/admin/jwt-claims` | Yes | `ADMIN` |

---

## How to Start / Stop

### Start Keycloak
```bash
cd ~/keycloak-spring-demo
sudo docker compose up -d
```

### Stop Keycloak
```bash
cd ~/keycloak-spring-demo
sudo docker compose down
```

### Check Keycloak logs
```bash
sudo docker logs keycloak -f
```

### Start Spring Boot
```bash
cd ~/keycloak-spring-demo
mvn spring-boot:run
```

---

## Get a JWT Token (curl)

### As testuser (USER role)
```bash
curl -s -X POST "http://52.65.212.193:8180/realms/demo-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-demo-client" \
  -d "client_secret=super-secret-client-key" \
  -d "username=testuser" \
  -d "password=password123" \
  -d "grant_type=password"
```

### As adminuser (ADMIN role)
```bash
curl -s -X POST "http://52.65.212.193:8180/realms/demo-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-demo-client" \
  -d "client_secret=super-secret-client-key" \
  -d "username=adminuser" \
  -d "password=password123" \
  -d "grant_type=password"
```

### Call a protected endpoint
```bash
TOKEN="<paste access_token here>"

curl http://52.65.212.193:8080/api/user/profile \
  -H "Authorization: Bearer $TOKEN"
```

### Run all tests at once
```bash
cd ~/keycloak-spring-demo
./test-api.sh 52.65.212.193
```

---

## Project File Structure

```
keycloak-spring-demo/
├── docker-compose.yml                        # Keycloak container
├── setup-keycloak.sh                         # One-time realm/client/user setup
├── test-api.sh                               # End-to-end API test script
├── pom.xml                                   # Maven dependencies
├── NOTES.md                                  # This file
└── src/main/
    ├── resources/
    │   └── application.yml                   # Spring Boot + Keycloak config
    └── java/com/demo/keycloak/
        ├── KeycloakDemoApplication.java       # Main entry point
        ├── config/
        │   └── SecurityConfig.java           # Security rules, JWT setup
        ├── security/
        │   └── KeycloakJwtRoleConverter.java # Maps KC roles → ROLE_USER/ADMIN
        ├── controller/
        │   ├── PublicController.java         # /api/public/** (no auth)
        │   ├── UserController.java           # /api/user/** (USER role)
        │   └── AdminController.java          # /api/admin/** (ADMIN role)
        └── dto/
            └── UserInfoDto.java              # Response DTO
```

---

## How JWT Auth Works (Flow)

```
1. Client sends username + password to Keycloak token endpoint
2. Keycloak returns a signed JWT (access_token)
3. Client sends request to Spring Boot with:
      Authorization: Bearer <JWT>
4. Spring Boot validates JWT signature using Keycloak's public key (JWK)
5. KeycloakJwtRoleConverter reads realm_access.roles from JWT
6. Spring Security maps roles → ROLE_USER, ROLE_ADMIN
7. SecurityConfig / @PreAuthorize checks the role → allow or 403
```

### JWT Payload Example (decoded)
```json
{
  "sub": "14dd1c6b-9d5d-498f-8472-0bd7c652af0e",
  "preferred_username": "testuser",
  "email": "testuser@demo.com",
  "realm_access": {
    "roles": ["USER", "offline_access", "uma_authorization"]
  },
  "iss": "http://52.65.212.193:8180/realms/demo-realm",
  "exp": 1748396700
}
```

---

## Known Issues & Fixes

### "HTTPS required" error in browser
Keycloak defaults new realms to `sslRequired: external`.
**Fix applied** — disabled via Admin API:
```bash
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli&username=admin&password=admin123&grant_type=password" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s -X PUT "http://localhost:8180/admin/realms/demo-realm" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"sslRequired":"none"}'

curl -s -X PUT "http://localhost:8180/admin/realms/master" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"sslRequired":"none"}'
```
> **Warning:** Only do this in dev/learning environments. Use HTTPS in production.

### `docker-compose: command not found`
This Ubuntu installed Docker Compose v2. Use `docker compose` (space, not hyphen).

### Cannot reach public IP from inside EC2
AWS does not support hairpin NAT by default.
Always use `localhost` or `127.0.0.1` when calling services from within the same EC2 instance.

---

## Dependencies (pom.xml)

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-security` | Spring Security |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `spring-boot-starter-oauth2-client` | Browser login redirect flow |
| `lombok` | Boilerplate reduction |

---

## Next Steps to Explore

- [ ] Add a React/Vue frontend that does browser-based login via Keycloak
- [ ] Use Keycloak Admin REST API to manage users programmatically
- [ ] Add HTTPS with a self-signed cert or Let's Encrypt
- [ ] Create custom Keycloak themes
- [ ] Configure social login (Google, GitHub) in Keycloak
- [ ] Set up token refresh flow
- [ ] Add Keycloak groups and map them to Spring roles
