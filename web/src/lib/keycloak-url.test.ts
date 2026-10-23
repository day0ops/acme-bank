import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolveKeycloakUrl } from '@/lib/auth'

describe('resolveKeycloakUrl', () => {
  afterEach(() => {
    delete window.__RUNTIME_CONFIG__
    vi.unstubAllEnvs()
  })

  it('prefers the nginx-injected runtime config over everything else', () => {
    window.__RUNTIME_CONFIG__ = { KEYCLOAK_URL: 'https://keycloak.example.com' }
    vi.stubEnv('VITE_KEYCLOAK_URL', 'https://build-time.example.com')
    expect(resolveKeycloakUrl()).toBe('https://keycloak.example.com')
  })

  it('falls back to the Vite build-time env var when runtime config is absent', () => {
    vi.stubEnv('VITE_KEYCLOAK_URL', 'https://build-time.example.com')
    expect(resolveKeycloakUrl()).toBe('https://build-time.example.com')
  })

  it('falls back to localhost when neither is set', () => {
    expect(resolveKeycloakUrl()).toBe('http://localhost:8080')
  })
})
