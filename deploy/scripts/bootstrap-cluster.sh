#!/usr/bin/env bash
# deploy/scripts/bootstrap-cluster.sh
# Creates the local kind cluster and installs Istio ambient mesh.
set -euo pipefail

cd "$(dirname "$0")/../.."

# kind drives whatever container runtime is available. Prefer Docker; fall
# back to Podman's experimental provider when the Docker daemon isn't up.
if ! docker info >/dev/null 2>&1; then
  if command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
    export KIND_EXPERIMENTAL_PROVIDER=podman
  fi
fi

if ! kind get clusters | grep -q '^acme-bank$'; then
  kind create cluster --config deploy/kind-config.yaml
else
  echo "kind cluster 'acme-bank' already exists, reusing it"
fi

kubectl config use-context kind-acme-bank

helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

kubectl create namespace istio-system --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install istio-base istio/base -n istio-system --set defaultRevision=default --wait
helm upgrade --install istiod istio/istiod -n istio-system --set profile=ambient --wait
helm upgrade --install istio-cni istio/cni -n istio-system --set profile=ambient --wait
helm upgrade --install ztunnel istio/ztunnel -n istio-system --wait

echo "Istio ambient mesh installed. Verifying:"
kubectl get pods -n istio-system
