import { http, passthrough } from 'msw'
import { beforeAll, describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'

// This test requires Docker/podman and verifies Keycloak's actual token
// endpoint accepts the realm/client configuration this app is built
// against - it does not exercise keycloak-js's browser-redirect flow
// (that needs a real browser, covered by Task 7's Playwright suite), only
// that the realm/client/credentials this app is configured for actually work.
//
// Requires a live Keycloak instance at localhost:18080 with the real realm
// import (see web-task-2-report.md for the exact container command used).
//
// test-setup.ts's global MSW server runs with onUnhandledFrame: 'error' so
// other tests catch accidental real-network calls; that turns this file's
// deliberate real fetch into a hard failure unless it's explicitly declared
// a handled (pass-through) request.
describe('Keycloak realm configuration', () => {
  beforeAll(() => server.use(http.post('http://localhost:18080/*', () => passthrough())))

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
