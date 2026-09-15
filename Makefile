# Harbor local-fleet for grokbot-auth (edge + 2 apps + 2 DBs).
COMPOSE ?= docker compose
EDGE_PORT ?= 8080
export EDGE_PORT

.PHONY: full-up down down-clean build logs smoke wait-edge env-check

env-check:
	@test -f .env || (echo "Copy .env.example to .env and set placeholders"; exit 1)

full-up: env-check down-clean
	$(COMPOSE) build --pull
	$(COMPOSE) up -d
	$(MAKE) wait-edge
	$(MAKE) smoke

down:
	$(COMPOSE) down --remove-orphans

down-clean:
	$(COMPOSE) down --remove-orphans --rmi local
	docker image prune -f

build:
	$(COMPOSE) build

logs:
	$(COMPOSE) logs -f edge signup-service login-service

wait-edge:
	@echo "waiting for edge on :$(EDGE_PORT)"
	@i=0; \
	until curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$(EDGE_PORT)/v1/auth/signup" | grep -Eq '405|415|400|404|429'; do \
	  i=$$((i+1)); \
	  if [ $$i -gt 90 ]; then echo "edge timeout"; $(COMPOSE) ps; exit 1; fi; \
	  sleep 2; \
	done
	@echo "edge reachable"

smoke:
	BASE_URL="http://127.0.0.1:$(EDGE_PORT)" ./scripts/smoke.sh
