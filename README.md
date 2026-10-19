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
- `platform/` - Keycloak realm config and Kubernetes manifests (namespaces,
  Gateway/HTTPRoutes, AuthorizationPolicy)
- `deploy/helm/` - per-service Helm charts; `deploy/scripts/` - local kind +
  Istio ambient mesh + kgateway bring-up

## Getting started

Run `make help` to see available commands, or `make cluster-up` to bring up a
local kind cluster with the ambient mesh, kgateway, and routing scaffolding.

See [docs/architecture.md](docs/architecture.md#known-gaps-not-yet-implemented)
for what's built versus what's still a known gap.
