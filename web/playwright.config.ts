import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    // Port 8080 (this app's default VITE_KEYCLOAK_URL) is already bound by
    // unrelated local software on this machine, so the E2E Keycloak
    // container runs on 18080 instead - the same port Task 2's auth.test.ts
    // already uses for the same reason. Harmless for the guest-flow test,
    // which never talks to Keycloak.
    env: { VITE_KEYCLOAK_URL: 'http://localhost:18080' },
  },
  use: { baseURL: 'http://localhost:5173' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
