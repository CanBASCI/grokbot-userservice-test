# Spec: grokbot-userservice-test (Maven artifact: grokbot-auth)

Repo: https://github.com/CanBASCI/grokbot-userservice-test  
Audience: backend engineers implementing or reviewing this stack.  
Law tour tip (Harbor): `2320a40` — Anvil law patch `b478ae2`, prior SPEC `d0acaa0`.

---

## 1. Purpose and scope

Spring Boot **4.1.1** + Java **25** identity reference: public signup / login / refresh behind an application API gateway; internal gRPC signup + login services; separate Postgres + Flyway each.

**In scope:** layered microservices, RFC 9457 problem+json, ErrorInfo.reason, Idempotency-Key on signup, W3C trace_id, round_robin gRPC clients, Compose REPLICAS_*.

**Out of scope:** email verify, OAuth, MFA, reset, Kafka/outbox, mandatory OpenAPI/DLQ, nginx-as-API-entry, Lombok, shared DB.

---

## 2. Architecture

```
Client
   │  HTTP JSON  :8080
   ▼
api-gateway          — REST + infrastructure gRPC clients + problem+json; no business DB
   │
   ├── gRPC Register (+ metadata idempotency-key) ► signup-service:9090 ► signup-db
   │
   └── gRPC Login / Refresh ► login-service:9090 ► login-db
                                      │
                                      └── gRPC VerifyCredentials ► signup:9090
```

- Host publishes **only** gateway `${HTTP_PORT:-8080}:8080`.
- Business gRPC `GRPC_PORT=9090` is private (`expose`, not `ports`).
- Gateway never calls VerifyCredentials.
- nginx is not the API entry (`edge/README.md`).

---

## 3. Maven modules

| Module | Role | Surface |
|--------|------|---------|
| `auth-proto` | Protobuf stubs + common protos | — |
| `api-gateway` | Public REST, health, tracing | HTTP `HTTP_PORT` (default 8080) |
| `signup-service` | Users + password + idempotency store | gRPC 9090 |
| `login-service` | JWT + refresh sessions | gRPC 9090 |

**Pins (current):**

- Spring Boot **4.1.1**, Java **25**
- JJWT **0.13.0** + **jjwt-gson** (not jjwt-jackson)
- `protobuf-maven-plugin` **5.1.9**
- No MapStruct, no Lombok, no `javax.annotation-api`
- Flyway + Postgres; `ddl-auto=validate`

---

## 4. Public REST (gateway only)

Base: `http://host:8080`  
Prefix: `/v1/auth`  
Probes: `GET /ready` (process), `GET /health` (gRPC channel reachability)

### 4.1 Problem+json (RFC 9457)

Every 4xx/5xx:

| Member | Rule |
|--------|------|
| `type` | `https://grokbot.local/errors/{kebab-from-code}` (not `about:blank`) |
| `title` | Short English HTTP title |
| `status` | HTTP status number |
| `detail` | Client-safe; **5xx always** `An unexpected error occurred.` |
| `code` | UPPER_SNAKE = `ErrorInfo.reason` |
| `trace_id` | W3C trace-id (32 hex), always present |

FromGRPC: prefer `google.rpc.ErrorInfo.reason`; trailer `error-code` fallback; never collapse to `GATEWAY_*`.

### 4.2 `POST /v1/auth/signup` → **201**

Headers: `Content-Type: application/json`, **`Idempotency-Key` required** (non-blank, max 128, printable ASCII).

Body: `{ "email", "password" }` (password 8–128).  
Response: `{ "userId", "email" }`.

| HTTP | code |
|------|------|
| 201 | created or idempotent replay |
| 400 | EMAIL_*, PASSWORD_*, UNKNOWN_PROPERTY, INVALID_JSON, IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_INVALID |
| 409 | EMAIL_TAKEN, IDEMPOTENCY_KEY_BODY_MISMATCH |

Gateway forwards `Idempotency-Key` → gRPC metadata `idempotency-key`. Signup-service owns the replay store (Flyway `V2__signup_idempotency.sql`). Same key + fingerprint → same 201 body; same key + different body → `IDEMPOTENCY_KEY_BODY_MISMATCH`.

### 4.3 `POST /v1/auth/login` → **200**

Body: email + password (max 128). No Idempotency-Key.  
Response: `TokenResponse` `{ accessToken, refreshToken, tokenType:"Bearer", expiresIn }`.  
401: `INVALID_CREDENTIALS`.

### 4.4 `POST /v1/auth/refresh` → **200**

Body: `{ "refreshToken" }`. Rotation.  
401: `INVALID_REFRESH_TOKEN`.

---

## 5. gRPC

**auth.signup.v1:** `Register`, `VerifyCredentials`  
**auth.login.v1:** `Login`, `Refresh`

Errors: `Status` + `google.rpc.ErrorInfo{ reason=CODE, domain=auth.signup|auth.login }`.  
Clients: `default.load-balancing-policy: round_robin` (gateway + login→signup).

---

## 6. Domain notes

- Signup: normalize email; BCrypt; unique email; unique-violation → `EMAIL_TAKEN`.
- VerifyCredentials: dummy BCrypt on miss (timing).
- Login: verify via signup gRPC; JWT access (`sub`, `email`, `typ=access`); opaque refresh SHA-256 stored; rotation + reuse → revoke-all.
- `DomainException` carries reason only (no HTTP status/title in domain).

---

## 7. Data model

**signup `users`:** id, email UNIQUE, password_hash, created_at  
**signup idempotency:** key, request_hash, user_id, email, created_at  
**login `refresh_tokens`:** id, user_id, email, token_hash UNIQUE, expires_at, revoked_at, created_at  

No cross-service FKs.

---

## 8. Compose / Harbor

- Images: `postgres:18`; Dockerfiles start with `# syntax=docker/dockerfile:1`; Temurin 25.
- Publish gateway only; gRPC 9090 private.
- App services: **no** `container_name` (scale-safe). DBs may keep names.
- Env: `REPLICAS_GATEWAY=1` (must stay 1), `REPLICAS_SIGNUP_SERVICE`, `REPLICAS_LOGIN_SERVICE`.
- Make: `--scale` signup/login; wait on `GET /ready`; smoke host `:9090` closed + signup→login→refresh.
- Process listen: gateway `HTTP_PORT=8080` in container; host map `${HTTP_PORT:-8080}:8080`.

```bash
cp .env.example .env
make full-up
```

---

## 9. Tests

```bash
mvn -q test   # 42 green (signup 18 + login 15 + gateway 9) as of b478ae2
```

Repository tests: Flyway + H2. Production dialect: PostgreSQL. Maven ≥ 3.9.11.

---

## 10. Observability (current)

- Gateway: Micrometer/OTel starter present; OTLP export off locally.
- Logs/MDC: `trace_id` / `span_id` / `code` on error paths.
- Public probes: `/ready`, `/health` on gateway only — do not scrape `:9090`.

---

## 11. Security summary

Public surface = gateway HTTP only. Refresh hashed + rotated. Dummy bcrypt on miss. No secrets in images. Signup race unique → `EMAIL_TAKEN`. Rate limit / mTLS not in this reference.

---

## 12. Developer checklist

1. Contract change → Relay freeze first (`code` == ErrorInfo.reason).
2. Business rule → owning service domain; gateway maps only.
3. Schema → Flyway SQL; never ddl-auto create/update.
4. Creating POST → Idempotency-Key + store in owning service.
5. Errors → ErrorInfo + problem members including `trace_id` and type URI.
6. gRPC clients → round_robin; `GRPC_PORT=9090` unpublished.
7. Scale → `REPLICAS_*` + compose `--scale`; never freeze counts in code.
8. Tests + smoke; no secrets in chat/git.

---

## 13. Curl

```bash
curl -s -X POST http://127.0.0.1:8080/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"email":"dev@example.com","password":"Password123!"}'

curl -s -X POST http://127.0.0.1:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@example.com","password":"Password123!"}'

curl -fsS http://127.0.0.1:8080/ready
```

---

## 14. One-line summary

Client → Spring API gateway → gRPC signup/login → separate Postgres, with RFC 9457 + ErrorInfo + signup Idempotency-Key + REPLICAS_* fleet — team-law reference implementation.
