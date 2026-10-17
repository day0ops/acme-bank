import { describe, expect, it } from 'vitest'

// This test requires Docker/podman and verifies Keycloak's actual token
// endpoint accepts the realm/client configuration this app is built
// against - it does not exercise keycloak-js's browser-redirect flow
// (that needs a real browser, covered by Task 7's Playwright suite), only
// that the realm/client/credentials this app is configured for actually work.
//
// Requires a live Keycloak instance at localhost:18080 with the real realm
// import (see web-task-2-report.md for the exact container command used).
describe('Keycloak realm configuration', () => {
  it('issues a token for the configured realm and client using the password grant', async () => {
    const response = await fetch('http://localhost:18080/realms/acme-bank/protocol/openid-connect/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: 'acme-bank-web',
        username: 'alice',
        password: 'customer123',
      }),
    })
    expect(response.ok).toBe(true)
    const body = await response.json()
    expect(body.access_token).toBeTruthy()
  })
})
