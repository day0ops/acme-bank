# Acme Bank

[![CI](https://github.com/day0ops/acme-bank/actions/workflows/ci.yml/badge.svg)](https://github.com/day0ops/acme-bank/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/day0ops/acme-bank)](LICENSE)
[![Go Version](https://img.shields.io/badge/go-1.27%2B-00ADD8)](go.work)

A polyglot core-banking microservices demo built on Istio ambient mesh and
kgateway. Full architecture: [docs/architecture.md](docs/architecture.md).

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
