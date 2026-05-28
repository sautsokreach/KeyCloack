#!/usr/bin/env bash
# =============================================================
# Test all API endpoints after setup
# Usage: ./test-api.sh <EC2_PUBLIC_IP>
# =============================================================

KC_IP="${1:-localhost}"
KC_URL="http://${KC_IP}:8180"
APP_URL="http://${KC_IP}:8080"
REALM="demo-realm"
CLIENT_ID="spring-demo-client"
CLIENT_SECRET="super-secret-client-key"

get_token() {
  local USERNAME=$1
  local PASSWORD=$2
  curl -s -X POST "${KC_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=${CLIENT_ID}" \
    -d "client_secret=${CLIENT_SECRET}" \
    -d "username=${USERNAME}" \
    -d "password=${PASSWORD}" \
    -d "grant_type=password" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token','ERROR: '+d.get('error_description','unknown')))"
}

echo "======================================================"
echo " 1. Public endpoint (no token)"
echo "======================================================"
curl -s "${APP_URL}/api/public/hello" | python3 -m json.tool

echo ""
echo "======================================================"
echo " 2. Get USER token for testuser"
echo "======================================================"
USER_TOKEN=$(get_token "testuser" "password123")
echo "Token (first 60 chars): ${USER_TOKEN:0:60}..."

echo ""
echo "======================================================"
echo " 3. Access /api/user/profile with USER token"
echo "======================================================"
curl -s "${APP_URL}/api/user/profile" \
  -H "Authorization: Bearer ${USER_TOKEN}" | python3 -m json.tool

echo ""
echo "======================================================"
echo " 4. Try /api/admin/dashboard with USER token (should 403)"
echo "======================================================"
curl -s -w "\nHTTP Status: %{http_code}\n" \
  "${APP_URL}/api/admin/dashboard" \
  -H "Authorization: Bearer ${USER_TOKEN}"

echo ""
echo "======================================================"
echo " 5. Get ADMIN token for adminuser"
echo "======================================================"
ADMIN_TOKEN=$(get_token "adminuser" "password123")
echo "Token (first 60 chars): ${ADMIN_TOKEN:0:60}..."

echo ""
echo "======================================================"
echo " 6. Access /api/admin/dashboard with ADMIN token"
echo "======================================================"
curl -s "${APP_URL}/api/admin/dashboard" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | python3 -m json.tool

echo ""
echo "======================================================"
echo " 7. View all JWT claims"
echo "======================================================"
curl -s "${APP_URL}/api/admin/jwt-claims" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | python3 -m json.tool
