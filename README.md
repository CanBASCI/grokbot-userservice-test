# grokbot-auth

Anvil/Relay auth multi-module Maven project: **signup-service** (8081) and **login-service** (8082).
No Spring gateway app. No shared database. Thin **nginx edge** via Docker Compose (Harbor).

## Modules

| Module | Port | Responsibility |
|--------|------|----------------|
| `signup-service` | 8081 | Public signup; users + password_hash; internal credential verify |
| `login-service` | 8082 | Login + refresh; JWT access + opaque refresh rotation |

## Versions

- Spring Boot **4.1.1**
- Java **25** LTS
- MapStruct **1.6.3** (no Lombok)
- JJWT **0.12.6**
- Flyway + PostgreSQL (BOM); `ddl-auto=validate`

## Validation defaults (signup)

- Email: required, must look like an email (`local@domain.tld`), normalized to lowercase
- Password: required, min **8**, max **128** characters

## Required environment

### signup-service

```bash
export SERVER_PORT=8081
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/signup
export SPRING_DATASOURCE_USERNAME=signup
export SPRING_DATASOURCE_PASSWORD=signup
```

### login-service

```bash
export SERVER_PORT=8082
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/login
export SPRING_DATASOURCE_USERNAME=login
export SPRING_DATASOURCE_PASSWORD=login
export JWT_SECRET='replace-with-at-least-32-chars-secret'
export JWT_ACCESS_TTL_SECONDS=900          # optional, default 900
export REFRESH_TTL_SECONDS=2592000         # optional, default 30 days
export SIGNUP_BASE_URL=http://localhost:8081
```

Boot **fails** if `JWT_SECRET` is missing/blank/`<32` chars or if `SIGNUP_BASE_URL` is blank.

## Run tests

From the parent directory:

```bash
cd /workspace/grokbot-auth
mvn -q test
```

Per module:

```bash
mvn -q -pl signup-service test
mvn -q -pl login-service test
```

Repository slice tests use **Flyway + H2** (PostgreSQL mode) because Docker/Testcontainers is not assumed on every box. Production dialect remains PostgreSQL.

## Run services (local)

```bash
mvn -pl signup-service spring-boot:run
mvn -pl login-service spring-boot:run
```

## Relay endpoints (on each service for now)

- `POST /v1/auth/signup` → 201 `{userId,email}` | 409 `EMAIL_TAKEN` | validation codes
- `POST /internal/v1/credentials/verify` (signup) → 200 `{userId,email}` | 401 `INVALID_CREDENTIALS`
- `POST /v1/auth/login` → 200 tokens | 401 `INVALID_CREDENTIALS`
- `POST /v1/auth/refresh` → 200 rotated tokens | 401 `INVALID_REFRESH_TOKEN`

Errors: `application/problem+json` with `type`, `title`, `status`, `detail`, `code`.


## Docker Compose (Harbor)

Public edge only on **`:8080`**. App and DB ports are **not** published. Flyway runs on app boot. Named volumes kept on `down` (no wipe).

### Security notes (Sentinel)

- Edge returns **404** for `/internal/**` — never proxied to the host.
- `login-service` calls signup verify over the private `auth_net` only: `http://signup-service:8081` (plain HTTP, internal-only). mTLS is optional later.
- DB ports are not mapped to the host.
- Secrets via `.env` (see `.env.example`). Do not bake secrets into images.

### Bring-up

```bash
cd /workspace/grokbot-auth   # or your clone of CanBASCI/grokbot-userservice-test
cp .env.example .env        # edit placeholders (JWT_SECRET >= 32 chars)
make full-up
# equivalent:
# docker compose down --remove-orphans --rmi local && docker image prune -f
# docker compose up -d --build
# BASE_URL=http://127.0.0.1:8080 ./scripts/smoke.sh
```

### Smoke

`scripts/smoke.sh` checks: edge denies `/internal`, then signup → login → refresh.

### Tear down (keep DB volumes)

```bash
make down
# or remove local app images too:
make down-clean
```

Do **not** run `docker compose down -v` / volume prune / `docker system prune -a` unless a data wipe was explicitly ordered.

## Push yourself later (no tokens in this repo)

Preferred remote for Clerk/Anvil experiments:

```bash
cd /workspace/grokbot-auth
git init
git add .
git commit -m "Add signup and login Spring Boot services"
git branch -M main
git remote add origin git@github.com:CanBASCI/grokbot-userservice-test.git
git push -u origin main
```

Alternate mentioned earlier: `https://github.com/CanBASCI/grokbot-test.git` — use your own credentials; do not paste PATs into chat.
