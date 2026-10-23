import Keycloak from 'keycloak-js'

declare global {
  interface Window {
    __RUNTIME_CONFIG__?: { KEYCLOAK_URL?: string }
  }
}

/**
 * Resolves the Keycloak base URL the browser should redirect to.
 *
 * `import.meta.env.VITE_*` is inlined at build time, so it can't vary by
 * deployment without a distinct image per hostname. `window.__RUNTIME_CONFIG__`
 * is written to `/config.js` by nginx's envsubst templating at container
 * start (see `public/config.js.template`), matching how every other service
 * in this app reads its external URLs from env vars at runtime rather than
 * build time.
 */
export function resolveKeycloakUrl(): string {
  return (
    window.__RUNTIME_CONFIG__?.KEYCLOAK_URL || import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080'
  )
}

/** Builds a Keycloak client configured for the acme-bank realm/client. */
export function createKeycloak(): Keycloak {
  return new Keycloak({
    url: resolveKeycloakUrl(),
    realm: 'acme-bank',
    clientId: 'acme-bank-web',
  })
}

let keycloakInstance: Keycloak | null = null

/** Returns the app-wide Keycloak client singleton, creating it on first use. */
export function getKeycloak(): Keycloak {
  if (!keycloakInstance) {
    keycloakInstance = createKeycloak()
  }
  return keycloakInstance
}
