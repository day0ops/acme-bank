# Keycloak realm configuration

`realm-export.json` in this directory is the canonical copy of the Acme Bank
realm definition.

Helm's `.Files.Get` can only read files inside the chart's own directory tree
(a path like `../../../platform/keycloak/realm-export.json` silently resolves
to an empty string instead of erroring). Because of that restriction, the
Keycloak chart at `deploy/helm/keycloak/` keeps a build-artifact copy at
`deploy/helm/keycloak/files/realm-export.json`, loaded into the
`keycloak-realm-config` ConfigMap. If you change the realm, update both
copies (or regenerate the chart's copy from this one before packaging/
deploying).
