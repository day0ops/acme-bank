# Keycloak realm configuration

`realm-export.json` in this directory is the canonical copy of the Acme Bank
realm definition.

Helm's `.Files.Get` can only read files inside the chart's own directory tree
(a path like `../../../platform/keycloak/realm-export.json` silently resolves
to an empty string instead of erroring). Because of that restriction,
`deploy/helm/keycloak/files/realm-export.json` is a relative symlink to this
file rather than a separate copy, so the chart can load it via `.Files.Get`
while there is still only one real file to keep in sync.
