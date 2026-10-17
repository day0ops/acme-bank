import Keycloak from 'keycloak-js'

/** Builds a Keycloak client configured for the acme-bank realm/client. */
export function createKeycloak(): Keycloak {
  return new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8080',
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
