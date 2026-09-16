# Clerk Patch Report — Relay Freeze + Section F

**Repo:** `CanBASCI/grokbot-userservice-test` (`/workspace/grokbot-auth`)  
**Base:** `d0acaa0`  
**SHA:** _(filled after push)_  
**Tarih (TR / Europe/Istanbul):** 2026-09-16  

## Özet

Relay FREEZE + Section F (jjwt 0.13 / jakarta / MapStruct kaldırma) uygulandı; `mvn -q test` yeşil; `origin/main` push edildi.

## Test

- `mvn -q test` → **BUILD SUCCESS**
- **42** test (signup 18 + login 15 + gateway 9)

## Değişen dosyalar (identifiers)

### Parent / deps (Section F)
- `pom.xml` — jjwt **0.13.0**, MapStruct kaldırıldı, `protobuf-maven-plugin` **5.1.9** pin, `proto-google-common-protos` BOM
- `auth-proto/pom.xml` — `javax.annotation-api` kaldırıldı (`@generated=omit` zaten Boot); `proto-google-common-protos`
- `login-service/pom.xml` — `jjwt-gson` (jackson yerine), tracing + common-protos
- `signup-service/pom.xml` — tracing + common-protos
- `api-gateway/pom.xml` — `spring-boot-starter-opentelemetry` + common-protos

### ErrorInfo / problem+json / DomainException
- `signup-service/.../GrpcStatusMapper.java` — `ErrorInfo{reason, domain=auth.signup}` + trailer `error-code`
- `login-service/.../GrpcStatusMapper.java` — `domain=auth.login`
- `api-gateway/.../ProblemDetailExceptionHandler.java` — type URI kebab, `code`+`trace_id`, 5xx fixed detail, ErrorInfo unpack, path-aware UNAUTHENTICATED
- `.../DomainException.java` (signup+login) — HTTP status/title kaldırıldı
- `.../ErrorCode.java` — idempotency + INTERNAL

### Idempotency (signup)
- `V2__signup_idempotency.sql` (+ H2) — key, request_hash, user_id, email, created_at
- `SignupIdempotencyStore*` / `RequestFingerprint` (SHA-256 email`\0`password)
- `IdempotencyKeyInterceptor` + `SignupGrpcService.register` replay/mismatch
- Gateway: `Idempotency-Key` required → gRPC metadata `idempotency-key`

### Gateway clients / health / tracing / LB
- `SignupAuthClient` / `LoginAuthClient` / `TraceparentClientInterceptor`
- `HealthController` — `GET /ready`, `GET /health` (channel reachability)
- `application.yml` — `server.port=${HTTP_PORT:${SERVER_PORT:8080}}`, `default.load-balancing-policy: round_robin`, OTel export off
- Login password `@Size(max=128)` → `PASSWORD_TOO_LONG`

### Docs / tests
- `README.md` — HTTP_PORT, Idempotency-Key, problem+json/trace_id, deps
- WebMvc + gRPC tests güncellendi (Idempotency-Key, ErrorInfo, trace_id)

## Deferred (Harbor / SPEC / Mason)

- **Harbor `compose.yml`:** dokunulmadı (DNS `service:9090` hedefi env ile; LB policy kodda; compose static:// defaults kalabilir)
- **SPEC.md** tam senkron (MapStruct/JJWT sürüm metinleri) — doküman lag
- Mason nits (opsiyonel) — atlandı
- OTLP export kapalı (local); prod collector wiring Harbor’a bırakıldı
- Deep `/health` channel-state tabanlı (lightweight health RPC / grpc.health.v1 yok)
- nginx-as-gateway / Kafka redesign yok (DO NOT)

