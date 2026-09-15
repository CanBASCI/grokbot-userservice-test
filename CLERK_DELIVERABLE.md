# CLERK_DELIVERABLE — grokbot-auth (Anvil)

## A. Yapılan değişiklikler (servisler + katmanlar)

İki Spring Boot servisi oluşturuldu (edge/gateway yok):

1. **signup-service** (port 8081)
   - `cmd/` — `SignupServiceApplication`, `DomainConfig` (port bean wiring)
   - `domain/` — `User`, `SignupService`, `VerifyCredentialsService`, portlar (`UserRepository`, `PasswordHasher`), sentinel hatalar
   - `repository/` — `UserEntity`, Spring Data, `UserRepositoryAdapter`; Flyway `V1__users.sql`
   - `transport/` — signup + internal verify controller, DTO, MapStruct, `problem+json` advice
   - `infrastructure/` — `BCryptPasswordHasher` (yalnızca burada)

2. **login-service** (port 8082)
   - `cmd/` — `LoginServiceApplication`, `DomainConfig`, `AppProperties`, `BootEnvValidator` (JWT_SECRET ≥32, SIGNUP_BASE_URL zorunlu)
   - `domain/` — `LoginService`, `RefreshService`, portlar (`RefreshTokenRepository`, `TokenIssuer`, `CredentialVerifierClient`)
   - `repository/` — `refresh_tokens` entity/adapter; Flyway `V1__refresh_tokens.sql` (signup DB’ye FK yok)
   - `transport/` — login + refresh controller, DTO, MapStruct, problem advice
   - `infrastructure/` — `JwtAccessTokenIssuer` (JJWT 0.12.6; claims: sub, email, iat, exp, typ=access), `HttpCredentialVerifierClient` (RestClient → signup `/internal/v1/credentials/verify`)
   - Opaque refresh: rastgele bağlayıcı string (JWT değil); SHA-256 hash DB’de; her refresh’te rotate (eski revoke)

## B. Sürümler

- Spring Boot parent **4.1.1**
- Java **25** LTS
- MapStruct **1.6.3** (Lombok yok)
- JJWT **0.12.6**
- Flyway + PostgreSQL driver (BOM)
- `spring.jpa.hibernate.ddl-auto=validate`
- Jackson 3 (`tools.jackson`) — Boot 4 ile

## C. Dosya ağacı özeti

```
grokbot-auth/
  pom.xml
  README.md
  CLERK_DELIVERABLE.md
  FILE_MANIFEST.txt
  signup-service/   (module)
  login-service/    (module)
```

Her modül: `src/main/java/.../{cmd,domain,repository,transport,infrastructure}`, `db/migration`, testler.

## D. Kod konumu (mutlak yol)

`/workspace/grokbot-auth`

## E. Kapsam dışı

- Edge/gateway Spring uygulaması
- Shared DB / cross-service FK
- Go implementasyonu
- GitHub’a zorunlu push (aşağıya bakın)
- Blob storage

## F. Doğrulama

```bash
cd /workspace/grokbot-auth
mvn -q test
```

Gerekli env (çalıştırma için):

- signup: `SPRING_DATASOURCE_*`, port 8081
- login: `SPRING_DATASOURCE_*`, `JWT_SECRET` (≥32), `SIGNUP_BASE_URL`, port 8082

Not: Docker/Testcontainers bu kutuda yok; repository testleri **Flyway + H2** (`db/migration-h2`) kullanır. Prod şema PostgreSQL `TIMESTAMPTZ` olarak kalır.

## G. Git

Tercih edilen remote: `git@github.com:CanBASCI/grokbot-userservice-test.git`

Push denemesi bu teslimatta yapılacaktır; başarısız olursa Anvil/Clerk’in kendisinin push etmesi gerekir (anahtar/chat’e secret koyma yok).

Manuel komutlar:

```bash
cd /workspace/grokbot-auth
git init
git add .
git commit -m "Add signup and login Spring Boot auth services"
git branch -M main
git remote add origin git@github.com:CanBASCI/grokbot-userservice-test.git
git push -u origin main
```

## H. Engeller

- Docker yok → Testcontainers PostgreSQL kullanılmadı; H2 test profili ile dar kapsamlı repository testleri
- Jackson 3 paketleri (`tools.jackson.databind.exc`) Boot 4’te güncellendi
