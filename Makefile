.DEFAULT_GOAL := help
.PHONY: help up down logs test smoke api db ui report defects

MVN = mvn --batch-mode --no-transfer-progress --file tests/pom.xml

help: ## Show the available commands
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | sed -E 's/:.*## /\t/' | expand -t 10

up: ## Start the bank and wait until it answers
	docker compose up -d --build --wait

down: ## Stop the bank and forget all data
	docker compose down

logs: ## Follow the log of the bank
	docker compose logs -f bank

test: ## Run every suite
	$(MVN) test

smoke: ## Run the smoke tests
	$(MVN) test -Dgroups=smoke

api: ## Run the API tests
	$(MVN) test -Dgroups=api

db: ## Run the database tests
	$(MVN) test -Dgroups=db

ui: ## Run the UI tests and the journeys
	$(MVN) test -Dgroups="ui | e2e"

report: ## Build the Allure report and open it
	$(MVN) allure:serve

defects: ## Prove that every seeded defect is caught
	scripts/seeded-defects.sh check-all
