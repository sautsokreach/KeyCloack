#!/usr/bin/env bash
# =============================================================
# Keycloak Realm + Client + User setup via Admin REST API
# Run this AFTER Keycloak is up: docker-compose up -d
# Usage: ./setup-keycloak.sh <EC2_PUBLIC_IP>
# =============================================================
set -e

KC_IP="${1:-localhost}"
KC_URL="http://${KC_IP}:8180"
ADMIN_USER="admin"
ADMIN_PASS="admin123"
REALM="demo-realm"
CLIENT_ID="spring-demo-client"
CLIENT_SECRET="super-secret-client-key"

echo "NOTE: Use 'docker compose' (v2) not 'docker-compose'"
echo ">>> Waiting for Keycloak at ${KC_URL} ..."
until curl -sf "${KC_URL}/health/ready" > /dev/null 2>&1; do
  sleep 3
done
echo ">>> Keycloak is ready."

# 1. Get admin access token
echo ">>> Getting admin token..."
ADMIN_TOKEN=$(curl -s -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

AUTH_HEADER="Authorization: Bearer ${ADMIN_TOKEN}"

# 2. Create realm
echo ">>> Creating realm: ${REALM}"
curl -s -X POST "${KC_URL}/admin/realms" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "{
    \"realm\": \"${REALM}\",
    \"enabled\": true,
    \"displayName\": \"Demo Realm\",
    \"registrationAllowed\": true,
    \"loginWithEmailAllowed\": true
  }" || echo "(realm may already exist)"

# 3. Create realm roles
for ROLE in USER ADMIN; do
  echo ">>> Creating realm role: ${ROLE}"
  curl -s -X POST "${KC_URL}/admin/realms/${REALM}/roles" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${ROLE}\"}" || echo "(role may exist)"
done

# 4. Create client
echo ">>> Creating client: ${CLIENT_ID}"
curl -s -X POST "${KC_URL}/admin/realms/${REALM}/clients" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"enabled\": true,
    \"publicClient\": false,
    \"secret\": \"${CLIENT_SECRET}\",
    \"standardFlowEnabled\": true,
    \"directAccessGrantsEnabled\": true,
    \"serviceAccountsEnabled\": true,
    \"redirectUris\": [\"http://${KC_IP}:8080/*\", \"http://localhost:8080/*\"],
    \"webOrigins\": [\"*\"]
  }" || echo "(client may exist)"

# 5. Create test users
create_user() {
  local USERNAME=$1
  local PASSWORD=$2
  local ROLE=$3

  echo ">>> Creating user: ${USERNAME} with role ${ROLE}"

  # Create user
  curl -s -X POST "${KC_URL}/admin/realms/${REALM}/users" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${USERNAME}\",
      \"email\": \"${USERNAME}@demo.com\",
      \"firstName\": \"${USERNAME^}\",
      \"lastName\": \"Test\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{
        \"type\": \"password\",
        \"value\": \"${PASSWORD}\",
        \"temporary\": false
      }]
    }"

  # Get user ID
  USER_ID=$(curl -s "${KC_URL}/admin/realms/${REALM}/users?username=${USERNAME}" \
    -H "${AUTH_HEADER}" \
    | python3 -c "import sys,json; users=json.load(sys.stdin); print(users[0]['id']) if users else print('')")

  if [ -z "$USER_ID" ]; then
    echo "!!! Could not find user ${USERNAME}"
    return
  fi

  # Get role
  ROLE_JSON=$(curl -s "${KC_URL}/admin/realms/${REALM}/roles/${ROLE}" \
    -H "${AUTH_HEADER}")

  # Assign role
  curl -s -X POST "${KC_URL}/admin/realms/${REALM}/users/${USER_ID}/role-mappings/realm" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "[${ROLE_JSON}]"

  echo "    User ${USERNAME} created and assigned role ${ROLE}"
}

create_user "testuser"  "password123" "USER"
create_user "adminuser" "password123" "ADMIN"

echo ""
echo "======================================================"
echo " SETUP COMPLETE"
echo "======================================================"
echo " Keycloak Admin UI : ${KC_URL}/admin"
echo " Realm             : ${REALM}"
echo " Client ID         : ${CLIENT_ID}"
echo " Client Secret     : ${CLIENT_SECRET}"
echo ""
echo " Test users:"
echo "   testuser  / password123  → role: USER"
echo "   adminuser / password123  → role: ADMIN"
echo ""
echo " Update application.yml:"
echo "   Replace <EC2_PUBLIC_IP> with: ${KC_IP}"
echo "   Replace YOUR_CLIENT_SECRET_HERE with: ${CLIENT_SECRET}"
echo "======================================================"
