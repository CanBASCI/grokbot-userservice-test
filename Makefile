# Harbor local-fleet: api-gateway + gRPC signup/login + 2 Postgres.
COMPOSE ?= docker compose
HTTP_PORT ?= 8080
REPLICAS_GATEWAY ?= 1
REPLICAS_SIGNUP_SERVICE ?= 1
REPLICAS_LOGIN_SERVICE ?= 1
export HTTP_PORT REPLICAS_GATEWAY REPLICAS_SIGNUP_SERVICE REPLICAS_LOGIN_SERVICE

.PHONY: full-up up down down-clean build logs smoke wait-gateway env-check

env-check:
	@test -f .env || (echo "Copy .env.example to .env and set placeholders"; exit 1)
	@if [ "$(REPLICAS_GATEWAY)" != "1" ]; then \
	  echo "REPLICAS_GATEWAY must be 1 (no LB in front of host :8080)"; exit 1; \
	fi

# Never --scale api-gateway (binds host 8080).
up: env-check
	$(COMPOSE) up -d \
	  --scale signup-service=$(REPLICAS_SIGNUP_SERVICE) \
	  --scale login-service=$(REPLICAS_LOGIN_SERVICE)

full-up: env-check down-clean
	$(COMPOSE) build --pull
	$(MAKE) up
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
	@echo "waiting for GET /ready on :$(HTTP_PORT)"
	@i=0; \
	until curl -fsS "http://127.0.0.1:$(HTTP_PORT)/ready" 2>/dev/null | grep -q UP; do \
	  i=$$((i+1)); \
	  if [ $$i -gt 120 ]; then echo "gateway ready timeout"; $(COMPOSE) ps; exit 1; fi; \
	  sleep 2; \
	done
	@echo "api-gateway /ready OK"

smoke:
	BASE_URL="http://127.0.0.1:$(HTTP_PORT)" ./scripts/smoke.sh
