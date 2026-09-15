#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
EMAIL="${SMOKE_EMAIL:-smoke-$(date +%s)@example.com}"
PASSWORD="${SMOKE_PASSWORD:-Password123!}"

echo "== edge blocks /internal =="
internal_code=$(curl -s -o /tmp/auth-internal.json -w "%{http_code}" \
  -X POST "${BASE_URL}/internal/v1/credentials/verify" \
  -H 'Content-Type: application/json' \
  -d '{"email":"x@y.z","password":"nope"}' || true)
test "${internal_code}" = "404"

echo "== POST ${BASE_URL}/v1/auth/signup =="
signup_code=$(curl -s -o /tmp/auth-signup.json -w "%{http_code}" \
  -X POST "${BASE_URL}/v1/auth/signup" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
echo "signup HTTP ${signup_code}"
test "${signup_code}" = "201"
grep -q '"email"' /tmp/auth-signup.json

echo "== POST ${BASE_URL}/v1/auth/login =="
login_code=$(curl -s -o /tmp/auth-login.json -w "%{http_code}" \
  -X POST "${BASE_URL}/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
echo "login HTTP ${login_code}"
test "${login_code}" = "200"
grep -q '"accessToken"' /tmp/auth-login.json
grep -q '"refreshToken"' /tmp/auth-login.json

REFRESH=$(sed -n 's/.*"refreshToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' /tmp/auth-login.json | head -1)
test -n "${REFRESH}"

echo "== POST ${BASE_URL}/v1/auth/refresh =="
refresh_code=$(curl -s -o /tmp/auth-refresh.json -w "%{http_code}" \
  -X POST "${BASE_URL}/v1/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"${REFRESH}\"}")
echo "refresh HTTP ${refresh_code}"
test "${refresh_code}" = "200"
grep -q '"accessToken"' /tmp/auth-refresh.json

echo "smoke OK (signup → login → refresh; /internal denied on edge)"
