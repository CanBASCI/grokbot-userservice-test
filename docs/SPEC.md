# Spec: grokbot-userservice-test (Maven artifact: grokbot-auth)

Repo: https://github.com/CanBASCI/grokbot-userservice-test  
Target audience: backend engineers implementing or reviewing this stack.

---

## 1. Purpose and scope

### 1.1 What is this?

A Spring Boot **4.1.1** + Java **25** identity (auth) reference system aligned with team law (Archon / Canon / Relay / Harbor / Sentinel). Public surface: signup, login, refresh. Internally two bounded contexts: **signup** (user aggregate + password) and **login** (session / tokens).

### 1.2 Why it exists

- Not a simple CRUD demo; a layered microservice reference.
- Public edge = Spring **application API gateway** on host **8080** (not nginx as API entry).
- Inter-service = **gRPC** on **9090** (not published to the host).
- Each service owns its **Postgres** database + **Flyway**; no shared tables / cross-service FKs.
- Pipeline proof: architecture → contracts → implementation law → code → security → Compose.

### 1.3 Explicitly out of scope

- Email verification, OAuth, MFA, password reset
- Kafka / transactional outbox (this product is a sync auth path)
- Mandatory OpenAPI, DLQ, empty notification/cron services
- Production TLS terminator (optional in front of gateway only; never replaces it)
- Lombok; JPA entities as `record`

---

## 2. Architecture

```
Client
   │  HTTP JSON  :8080
   ▼
api-gateway          ← no business rules / no DB; REST + gRPC clients + problem+json
   │
   ├── gRPC Register ------------► signup-service:9090 ──► signup-db (postgres:18)
   │
   └── gRPC Login / Refresh ────► login-service:9090 ──► login-db (postgres:18)
                                      │
                                      └── gRPC VerifyCredentials ► signup-service:9090
```

Rules:

- Gateway **never** calls `VerifyCredentials`; only `login-service` does.
- Host publishes **only** gateway `8080`. Ports `9090` and DB ports are not mapped with `ports:` (`expose` + private network).
- Both app containers listen on container-local `9090`; Compose DNS names (`signup-service` / `login-service`) distinguish them.

---

## 3. Maven module map

Parent: `com.example:grokbot-auth:0.1.0-SNAPSHOT` (`packaging=pom`)

| Module | Role | External surface |
|--------|------|------------------|
| `auth-proto` | Protobuf / gRPC stub jar | — |
| `api-gateway` | Public REST | HTTP `8080` |
| `signup-service` | User + password | gRPC `9090` |
| `login-service` | Token / session | gRPC `9090` |

Pinned versions:

- Spring Boot **4.1.1**
- Java **25**
- MapStruct **1.6.3**
- JJWT **0.12.6**
- Boot gRPC starters + `protobuf-maven-plugin` → requires **Maven ≥ 3.9.11**
- Flyway + PostgreSQL; `ddl-auto=validate` (schema is not code-first)

---

## 4. Repository layout

```
grokbot-userservice-test/
├── pom.xml
├── compose.yml
├── Makefile
├── .env.example
├── README.md
├── CLERK_DELIVERABLE.md
├── FILE_MANIFEST.txt
├── docs/
│   └── SPEC.md                 # this document
├── auth-proto/
│   └── src/main/proto/auth/{signup,login}/v1/*.proto
├── api-gateway/
│   └── src/main/java/.../{cmd,transport/{dto,rest,advice}}
├── signup-service/
│   └── src/main/java/.../{cmd,domain,repository,infrastructure,transport/grpc}
│   └── src/main/resources/db/migration/V1__users.sql
├── login-service/
│   └── ... same layer packages
│   └── db/migration/V1__refresh_tokens.sql, V2__refresh_token_email.sql
├── docker/                     # Dockerfile.{gateway,signup,login}
├── edge/README.md              # nginx is not the API entry
└── scripts/smoke.sh
```

### 4.1 In-service layer law (Canon)

Dependency direction: `transport` / `infrastructure` / `repository` → `domain` ← `cmd` (wiring).

| Package | Contains | Forbidden |
|---------|----------|-----------|
| `cmd` | Boot app, config, bean wiring | Business rules |
| `domain` | model, ports, usecases, `ErrorCode` / `DomainException` | Spring / Web / JPA / gRPC stubs |
| `repository` | JPA entity, Spring Data repo, adapter | REST controllers |
| `infrastructure` | BCrypt, JWT, gRPC client | Leaking Spring into domain |
| `transport/grpc` | gRPC service impl + status mapper | Direct DB access |

Gateway has no domain/repository: only `transport` + stub injection.

---

## 5. Public REST contract (gateway only)

Base: `http://host:8080`  
Prefix: `/v1/auth`  
Content-Type: `application/json`  
Errors: `application/problem+json`

### 5.1 `POST /v1/auth/signup` → **201**

Request (`SignupRequest` record):

```json
{ "email": "a@example.com", "password": "Password123!" }
```

Validation:

- `email`: `@NotBlank` → `EMAIL_REQUIRED`; `@Email` → `EMAIL_INVALID`
- `password`: length 8–128 → `PASSWORD_TOO_SHORT` / `PASSWORD_TOO_LONG`
- Unknown fields → `UNKNOWN_PROPERTY` (`@JsonIgnoreProperties(ignoreUnknown = false)` + Jackson fail-on-unknown)

Response (`SignupResponse`):

```json
{ "userId": "<uuid>", "email": "a@example.com" }
```

Domain conflict: **409** `EMAIL_TAKEN`

Gateway: `SignupServiceGrpc.Register` → map to 201 body.

### 5.2 `POST /v1/auth/login` → **200**

Request (`LoginRequest`):

```json
{ "email": "...", "password": "..." }
```

Response (`TokenResponse`):

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Failure: **401** `INVALID_CREDENTIALS`

### 5.3 `POST /v1/auth/refresh` → **200**

Request (`RefreshRequest`):

```json
{ "refreshToken": "..." }
```

Same `TokenResponse` shape.  
Invalid / revoked / expired / race: **401** `INVALID_REFRESH_TOKEN`  
Missing: **400** `REFRESH_TOKEN_REQUIRED`

### 5.4 Problem+JSON members

Handler: `ProblemDetailExceptionHandler`

| Member | Meaning |
|--------|---------|
| `type` | `about:blank` |
| `title` | e.g. `Bad Request` |
| `status` | HTTP status number |
| `detail` | human-readable (not an i18n key) |
| `code` | string business code (`EMAIL_TAKEN`, …) |

gRPC trailer key: `error-code` (ASCII). Status map:

- `INVALID_ARGUMENT` → 400
- `ALREADY_EXISTS` → 409
- `UNAUTHENTICATED` → 401
- other → 500 `INTERNAL` (no stack / binding leak to clients)

---

## 6. gRPC contracts (`auth-proto`)

### 6.1 `auth.signup.v1.SignupService`

- `Register(email, password) → (user_id, email)`
- `VerifyCredentials(email, password) → (user_id, email)` — **login-service client only**

### 6.2 `auth.login.v1.LoginService`

- `Login(email, password) → (access_token, refresh_token, token_type, expires_in)`
- `Refresh(refresh_token) → same shape`

`token_type` constant in domain: `"Bearer"` (`LoginService.TOKEN_TYPE`).

---

## 7. Domain behavior

### 7.1 Signup (`SignupService`)

1. Trim email; lower-case with `Locale.ROOT`; regex validate.
2. Password length 8–128.
3. `existsByEmail` → `EMAIL_TAKEN`.
4. Generate UUID; BCrypt hash; `created_at = clock.instant()`; save.

### 7.2 VerifyCredentials (`VerifyCredentialsService`) — Sentinel

- Even when the user is missing, run `matches()` against a **dummy BCrypt** hash (timing).
- Missing or mismatch → single error: `INVALID_CREDENTIALS` (no email enumeration).

### 7.3 Login (`LoginService`)

1. CredentialVerifier (gRPC → signup); absent → 401.
2. Issue access JWT.
3. Generate opaque refresh (32 bytes, URL-safe Base64); store **SHA-256 hex** hash only.
4. Raw refresh appears only in the response.

### 7.4 Refresh (`RefreshService`) — rotation + reuse detection

1. Raw token → hash → lookup.
2. Missing / expired / inactive → 401.
3. If **already revoked**: `revokeAllForUser(userId)` (theft path) → 401.
4. Atomic `claimActive(hash, now)`; failure → 401.
5. Issue a new token pair (`issueTokens`).

### 7.5 JWT (`JwtAccessTokenIssuer`)

- HMAC key = `JWT_SECRET` UTF-8 (must be ≥ 32 chars; boot fails otherwise)
- Claims: `sub` = userId, `email`, `typ=access`, `iat`, `exp`
- Default access TTL: **900s**; refresh TTL: **2592000s** (30 days)

---

## 8. Data model

### 8.1 `signup` DB — `users`

```sql
id UUID PK
email VARCHAR(320) NOT NULL UNIQUE
password_hash VARCHAR(255) NOT NULL
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

Flyway: `V1__users.sql`  
No cross-service FK. Login copies `user_id` + `email` onto refresh rows.

### 8.2 `login` DB — `refresh_tokens`

```sql
id UUID PK
user_id UUID NOT NULL          -- signup id; no FK
email VARCHAR(320) NOT NULL   -- added in V2
token_hash VARCHAR(255) UNIQUE
expires_at TIMESTAMPTZ
revoked_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ
INDEX (user_id)
```

Flyway: `V1__refresh_tokens.sql`, `V2__refresh_token_email.sql`  
Raw refresh is **never** stored.

---

## 9. Compose / operations (Harbor)

Services: `signup-db`, `login-db`, `signup-service`, `login-service`, `api-gateway`  
Network: `auth_net`  
Volumes: `signup-db-data`, `login-db-data` (`down` keeps volumes)

Startup order:

1. DB healthy (`pg_isready`)
2. signup healthy (TCP `:9090`)
3. login (signup + login-db healthy)
4. gateway (both gRPC healthy)
5. Gateway health: HTTP against signup path expecting 400/405/415 band

Commands:

```bash
cp .env.example .env   # JWT_SECRET ≥32, DB passwords
make full-up           # build + up + wait + smoke
make down              # volumes kept
make down-clean        # prune local images
make smoke
```

Smoke asserts:

1. Host `:9090` closed
2. signup 201 → login 200 → refresh 200

Env summary: `HTTP_PORT`, `SIGNUP_DB_*`, `LOGIN_DB_*`, `JWT_SECRET`, TTLs, `SIGNUP_GRPC_TARGET` / `LOGIN_GRPC_TARGET` (`static://service:9090`).

---

## 10. Tests

```bash
mvn -q test
```

Expected: **36** green (signup ~15, login ~15, gateway ~6).  
Repository slice tests use **Flyway + H2** (`db/migration-h2`). Production dialect remains PostgreSQL.

---

## 11. Security summary (Sentinel)

| Topic | Decision |
|-------|----------|
| Public surface | Gateway HTTP only |
| gRPC | Private network; `INTERNAL_API_KEY` removed |
| Passwords | BCrypt; no plaintext logs |
| Refresh | Opaque + SHA-256; rotation; reuse → revoke-all |
| Timing | Dummy bcrypt on verify miss path |
| Error leakage | Stack / binding / exception name off |
| Secrets | `.env` only; never commit or paste real secrets |
| Unknown JSON | Rejected |

---

## 12. Developer checklist (new feature)

1. Contract change → update `auth-proto` (+ Relay field rules) first.
2. Business rule → owning service `domain/usecase`; do not put it in the gateway.
3. Schema → new Flyway SQL; never invent tables via `ddl-auto`.
4. Public path → `/v1/...`; error `code` is a string.
5. gRPC default port `9090`; do not publish to host.
6. Add/adjust tests; run smoke when Compose is available.
7. Never put secrets in PRs or chat.

---

## 13. Quick curl examples

```bash
curl -s -X POST http://127.0.0.1:8080/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@example.com","password":"Password123!"}'

curl -s -X POST http://127.0.0.1:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@example.com","password":"Password123!"}'
```

---

## 14. One-line summary

This repository is a tested reference for **client → Spring API gateway → gRPC signup/login → separate Postgres**, with folders and ports fixed to that law.
