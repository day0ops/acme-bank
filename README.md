# Acme Bank

A polyglot core-banking microservices demo built on Istio ambient mesh and
kgateway. Full architecture: [docs/superpowers/specs/2026-10-16-acme-bank-platform-foundation-design.md](docs/superpowers/specs/2026-10-16-acme-bank-platform-foundation-design.md).

## Prerequisites

- Go 1.27+
- buf CLI (`brew install bufbuild/buf/buf`)
- `protoc-gen-go` - run `make tools` to install the pinned version

## Repo layout

- `services/` - one directory per microservice (Java, Go, Rust)
- `web/` - React + TypeScript UI
- `proto/` - shared Protobuf contracts (gRPC + event schemas)
- `platform/` - Keycloak, Redpanda, observability, and gateway configuration
- `deploy/helm/` - Helm charts for deployment

## Getting started

Run `make help` to see available commands.
