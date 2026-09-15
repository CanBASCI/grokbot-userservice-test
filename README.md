# grokbot-auth

Archon auth multi-module Maven project:

| Module | Port | Responsibility |
|--------|------|----------------|
| `api-gateway` | **8080** HTTP | Public REST only (`POST /v1/auth/signup\|login\|refresh`); gRPC clients; problem+json; **no** business DB / rules |
| `signup-service` | **9091** gRPC | `auth.signup.v1` Register + VerifyCredentials; users + BCrypt + Sentinel dummy bcrypt |
| `login-service` | **9092** gRPC | `auth.login.v1` Login + Refresh; JWT + opaque refresh; verifies credentials via signup gRPC |
| `auth-proto` | — | Frozen protobuf/gRPC stubs |

**nginx edge is deprecated for API entry.** Public clients hit `api-gateway`. gRPC ports must **not** be publicly exposed (network isolation). Harbor Compose update is later.

## Versions

- Spring Boot **4.1.1**
- Java **25** LTS
- MapStruct **1.6.3** (gateway maps records ↔ proto manually; no Lombok)
- JJWT **0.12.6**
- Boot gRPC starters (`spring-boot-starter-grpc-server` / `grpc-client`) + `io.github.ascopes:protobuf-maven-plugin`
- Flyway + PostgreSQL; `ddl-auto=validate`

## Architecture

```
Client --HTTP--> api-gateway:8080 --gRPC--> signup-service:9091
                              \--gRPC--> login-service:9092 --gRPC VerifyCredentials--> signup:9091
```

Gateway **never** calls `VerifyCredentials`; only `login-service` does.

## Required environment

### api-gateway

```bash
export SERVER_PORT=8080
export SIGNUP_GRPC_TARGET=static://localhost:9091
export LOGIN_GRPC_TARGET=static://localhost:9092
```

### signup-service

```bash
export GRPC_SERVER_PORT=9091
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/signup
export SPRING_DATASOURCE_USERNAME=signup
export SPRING_DATASOURCE_PASSWORD=signup
```

Use Spring profile `local` for laptop datasource defaults. `INTERNAL_API_KEY` is **removed** (gRPC is not public; isolate on private network).

### login-service

```bash
export GRPC_SERVER_PORT=9092
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/login
export SPRING_DATASOURCE_USERNAME=login
export SPRING_DATASOURCE_PASSWORD=login
export JWT_SECRET='replace-with-at-least-32-chars-secret'
export JWT_ACCESS_TTL_SECONDS=900
export REFRESH_TTL_SECONDS=2592000
export SIGNUP_GRPC_TARGET=static://localhost:9091
```

Boot **fails** if `JWT_SECRET` is missing/blank/`<32` chars. Prod datasource env-required (no weak defaults).

## Public REST (gateway only)

- `POST /v1/auth/signup` → 201 `{userId,email}` | 409 `EMAIL_TAKEN` | validation codes
- `POST /v1/auth/login` → 200 tokens | 401 `INVALID_CREDENTIALS`
- `POST /v1/auth/refresh` → 200 rotated tokens | 401 `INVALID_REFRESH_TOKEN`

Errors: `application/problem+json` with `type`, `title`, `status`, `detail`, `code`.

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

## Docker Compose (Harbor) — legacy note

Existing `compose.yml` / nginx edge still targets the old HTTP services. Treat nginx as **deprecated for API entry**; prefer `api-gateway:8080`. Compose will be updated later for gRPC + gateway.

## Push

Remote: `git@github.com:CanBASCI/grokbot-userservice-test.git` (SSH host alias configured). Do not paste secrets into chat.
