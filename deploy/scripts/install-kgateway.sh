#!/usr/bin/env bash
# deploy/scripts/install-kgateway.sh
# Installs the Kubernetes Gateway API CRDs and kgateway onto the cluster
# created by bootstrap-cluster.sh. Versions below were confirmed live
# against cr.kgateway.dev and cross-checked with kgateway's docs at the
# time of writing (kgateway v2.4.7, Gateway API v1.6.1) - update them there
# if this script needs to track a newer kgateway release.
set -euo pipefail

kubectl config use-context kind-acme-bank

# Gateway API CRDs (standard channel - kgateway doesn't bundle these itself)
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.6.1/standard-install.yaml

kubectl create namespace kgateway-system --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install kgateway-crds oci://cr.kgateway.dev/kgateway-dev/charts/kgateway-crds \
  --version 2.4.7 --namespace kgateway-system --wait
helm upgrade --install kgateway oci://cr.kgateway.dev/kgateway-dev/charts/kgateway \
  --version 2.4.7 --namespace kgateway-system --wait

echo "kgateway installed. Verifying:"
kubectl get pods -n kgateway-system
kubectl get gatewayclass
