JAVA_HOME_21 ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null || printf '%s' "$$JAVA_HOME")

.PHONY: dev-backend dev-frontend test build docker-up docker-down production-config production-up production-down

dev-backend:
	cd backend && JAVA_HOME=$(JAVA_HOME_21) PATH=$(JAVA_HOME_21)/bin:$$PATH mvn spring-boot:run

dev-frontend:
	cd frontend && pnpm dev

test:
	cd backend && JAVA_HOME=$(JAVA_HOME_21) PATH=$(JAVA_HOME_21)/bin:$$PATH mvn test
	cd frontend && pnpm build

build:
	cd backend && JAVA_HOME=$(JAVA_HOME_21) PATH=$(JAVA_HOME_21)/bin:$$PATH mvn -DskipTests package
	cd frontend && pnpm build

docker-up:
	docker compose up --build -d

docker-down:
	docker compose down

production-config:
	docker compose --env-file .env -f docker-compose.production.yml config --quiet

production-up:
	docker compose --env-file .env -f docker-compose.production.yml up --build -d

production-down:
	docker compose --env-file .env -f docker-compose.production.yml down
