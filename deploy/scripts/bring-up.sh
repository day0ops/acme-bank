#!/usr/bin/env bash
# deploy/scripts/bring-up.sh
# Full local bring-up: cluster + ambient mesh + kgateway + namespaces/routes.
set -euo pipefail
cd "$(dirname "$0")/../.."

./deploy/scripts/bootstrap-cluster.sh
./deploy/scripts/install-kgateway.sh
kubectl apply -f platform/k8s/namespaces.yaml
kubectl apply -f platform/k8s/gateway.yaml
kubectl apply -f platform/k8s/httproutes.yaml
kubectl apply -f platform/k8s/authorization-policy.yaml

echo "Platform scaffolding is up. Deploy individual services with:"
echo "  helm install <service> deploy/helm/<service> -n acme-bank"
