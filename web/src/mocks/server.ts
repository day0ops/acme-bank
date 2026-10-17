import { setupServer } from 'msw/node'
import { handlers } from './handlers'

// Shared Node request-interception server used by Vitest (see test-setup.ts)
// to mock the Dashboard BFF's REST API for this and later tasks' tests.
export const server = setupServer(...handlers)
