# CLERK_DELIVERABLE — grokbot-auth (Archon redesign)

## A. Yapılan değişiklikler

1. **auth-proto** — Relay freeze `auth.signup.v1` / `auth.login.v1` (`SignupService`, `LoginService`); jar + stubs
2. **api-gateway** (8080) — yalnızca public REST; gRPC client signup/login; record DTO + `@Valid`; problem+json; iş kuralı/DB yok; VerifyCredentials çağırmaz
3. **signup-service** (gRPC 9090) — HTTP + InternalCredentialsController kaldırıldı; `SignupGrpcService`; Flyway users + BCrypt + dummy bcrypt; INTERNAL_API_KEY yok
4. **login-service** (gRPC 9090) — HTTP AuthController kaldırıldı; `LoginGrpcService`; `GrpcCredentialVerifierClient`; JWT + opaque refresh + Sentinel claim/revoke-all
5. nginx edge API girişi deprecated; gRPC public değil (network isolation)

## B. Sürümler

Spring Boot **4.1.1**, Java **25**, MapStruct **1.6.3**, JJWT **0.12.6**, Boot gRPC starters + `protobuf-maven-plugin` (Maven ≥ 3.9.11).

## C. Dosya ağacı

```
grokbot-auth/
  auth-proto/
  signup-service/
  login-service/
  api-gateway/
```

## D. Kod konumu

`/workspace/grokbot-auth`

## E. Kapsam dışı

- Harbor Compose gRPC güncellemesi (sonra)
- Gateway'de iş kuralları / VerifyCredentials
- Lombok; JPA entity record

## F. Doğrulama

```bash
cd /workspace/grokbot-auth && mvn -q test
```

**36 tests green** (signup 15 + login 15 + gateway 6). Env: gateway channels; signup/login datasource; login `JWT_SECRET` (≥32); `SIGNUP_GRPC_TARGET`.

## G. Git

Push `main` → `CanBASCI/grokbot-userservice-test` SHA **31a1debe82937b7d00001b2a0064f35a3c22f040**. Chat'e secret yok.


## Port law (9090)

signup/login listen `${GRPC_PORT:${GRPC_SERVER_PORT:9090}}`. Gateway/login clients: Compose `static://signup-service:9090` / `static://login-service:9090`; local yml defaults `localhost:9090`. Host publish: only gateway 8080.
