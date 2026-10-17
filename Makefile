.PHONY: help proto-lint proto-gen

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

proto-lint: ## Lint the shared Protobuf contracts
	cd proto && buf lint

proto-gen: ## Generate Go code from the shared Protobuf contracts
	cd proto && buf generate
