# grokbot-auth

Archon auth multi-module Maven project:

| Module | Port | Responsibility |
|--------|------|----------------|
| `api-gateway` | **8080** HTTP | Public REST only (`POST /v1/auth/signup\|login\|refresh`); gRPC clients; problem+json; **no** business DB / rules |
| `signup-service` | **9090** gRPC | `auth.signup.v1` Register + VerifyCredentials; users + BCrypt + Sentinel dummy bcrypt |
| `login-service` | **9090** gRPC | `auth.login.v1` Login + Refresh; JWT + opaque refresh; verifies credentials via signup gRPC |
| `auth-proto` | — | Frozen protobuf/gRPC stubs |

Public clients hit **`api-gateway:8080`**. In Compose, gateway/login use `static://signup-service:9090` and `static://login-service:9090` (not published to host). Local laptop defaults in `application.yml` use `static://localhost:9090` for single-process debugging. gRPC (`9090` in each app container; DNS `signup-service` / `login-service`) stays on the private compose network — **not** published to the host. nginx is **not** the API entry (see `edge/README.md`).

## Versions

- Spring Boot **4.1.1**
- Java **25** LTS
- JJWT **0.13.0** (`jjwt-gson`; no MapStruct)
- `protobuf-maven-plugin` **5.1.9** + `proto-google-common-protos`
- Boot gRPC starters (`spring-boot-starter-grpc-server` / `grpc-client`) + `io.github.ascopes:protobuf-maven-plugin`
- Flyway + PostgreSQL; `ddl-auto=validate`

## Architecture

```
Client --HTTP--> api-gateway:8080 --gRPC--> signup-service:9090
                              \--gRPC--> login-service:9090 --gRPC VerifyCredentials--> signup:9090
```

Gateway **never** calls `VerifyCredentials`; only `login-service` does.

## Required environment

### api-gateway

```bash
export HTTP_PORT=8080   # preferred; falls back to SERVER_PORT, default 8080
export SERVER_PORT=8080
export SIGNUP_GRPC_TARGET=static://localhost:9090
export LOGIN_GRPC_TARGET=static://localhost:9090
```

### signup-service

```bash
export GRPC_SERVER_PORT=9090   # or GRPC_PORT=9090 (same default)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/signup
export SPRING_DATASOURCE_USERNAME=signup
export SPRING_DATASOURCE_PASSWORD=signup
```

Use Spring profile `local` for laptop datasource defaults. `INTERNAL_API_KEY` is **removed** (gRPC is not public; isolate on private network).

### login-service

```bash
export GRPC_SERVER_PORT=9090   # or GRPC_PORT=9090 (same default)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/login
export SPRING_DATASOURCE_USERNAME=login
export SPRING_DATASOURCE_PASSWORD=login
export JWT_SECRET='replace-with-at-least-32-chars-secret'
export JWT_ACCESS_TTL_SECONDS=900
export REFRESH_TTL_SECONDS=2592000
export SIGNUP_GRPC_TARGET=static://localhost:9090
```

Boot **fails** if `JWT_SECRET` is missing/blank/`<32` chars. Prod datasource env-required (no weak defaults).

## Public REST (gateway only)

- `POST /v1/auth/signup` (**Idempotency-Key** required) → 201 `{userId,email}` | 409 `EMAIL_TAKEN` / `IDEMPOTENCY_KEY_BODY_MISMATCH` | validation codes
- `GET /ready` process up; `GET /health` deep (signup+login gRPC reachability)
- `POST /v1/auth/login` → 200 tokens | 401 `INVALID_CREDENTIALS`
- `POST /v1/auth/refresh` → 200 rotated tokens | 401 `INVALID_REFRESH_TOKEN`

Errors: `application/problem+json` with `type` (`https://grokbot.local/errors/{kebab}`), `title`, `status`, `detail`, `code`, **`trace_id`** (5xx detail fixed).

gRPC domain errors attach trailing metadata `error-code` (UPPER_SNAKE). Gateway maps:
`INVALID_ARGUMENT`→400, `ALREADY_EXISTS`→409, `UNAUTHENTICATED`→401.

## Run tests

Requires **Maven ≥ 3.9.11** (Boot `protobuf-maven-plugin` 5.x / Sisu). System Maven 3.9.9 may fail plugin injection on this JDK.


```bash
cd /workspace/grokbot-auth
mvn -q test
```

Repository slice tests use **Flyway + H2**. Production dialect remains PostgreSQL.

## Run locally

```bash
mvn -pl signup-service spring-boot:run
mvn -pl login-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

## Docker Compose (Harbor)

Publish **only** `api-gateway` on host `${HTTP_PORT:-8080}`. Private: `signup-service:9090`, `login-service:9090`, `signup-db`/`login-db` (`postgres:18`). Flyway on app boot. Named volumes kept on `down` (no wipe / no `system prune -a`).

Requires **Maven ≥ 3.9.11** inside the image build (protobuf plugin).

```bash
cp .env.example .env   # set JWT_SECRET (≥32) and DB passwords
make full-up
# tear down (volumes kept):
make down
# also drop local app images:
make down-clean
```

Smoke (`scripts/smoke.sh`): asserts host `:9090` closed, then gateway signup → login → refresh.

## Push

Remote: `git@github.com:CanBASCI/grokbot-userservice-test.git` (SSH host alias configured). Do not paste secrets into chat.
