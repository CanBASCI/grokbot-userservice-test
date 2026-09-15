# Harbor local-fleet: api-gateway + gRPC signup/login + 2 Postgres.
COMPOSE ?= docker compose
HTTP_PORT ?= 8080
export HTTP_PORT

.PHONY: full-up down down-clean build logs smoke wait-gateway env-check

env-check:
	@test -f .env || (echo "Copy .env.example to .env and set placeholders"; exit 1)

full-up: env-check down-clean
	$(COMPOSE) build --pull
	$(COMPOSE) up -d
	$(MAKE) wait-gateway
	$(MAKE) smoke

down:
	$(COMPOSE) down --remove-orphans

down-clean:
	$(COMPOSE) down --remove-orphans --rmi local
	docker image prune -f

build:
	$(COMPOSE) build

logs:
	$(COMPOSE) logs -f api-gateway signup-service login-service

wait-gateway:
	@echo "waiting for api-gateway on :$(HTTP_PORT)"
	@i=0; \
	until curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$(HTTP_PORT)/v1/auth/signup" | grep -Eq '405|415|400|404|429'; do \
	  i=$$((i+1)); \
	  if [ $$i -gt 120 ]; then echo "gateway timeout"; $(COMPOSE) ps; exit 1; fi; \
	  sleep 2; \
	done
	@echo "api-gateway reachable"

smoke:
	BASE_URL="http://127.0.0.1:$(HTTP_PORT)" ./scripts/smoke.sh
