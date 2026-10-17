import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { server } from './mocks/server'

// jsdom has no IntersectionObserver; framer-motion's `whileInView` needs one
// to mount. A no-op stub is enough since tests don't assert on scroll state.
class IntersectionObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return []
  }
}
vi.stubGlobal('IntersectionObserver', IntersectionObserverStub)

// MSW intercepts fetchJson's network calls in tests; 'error' on an
// unhandled request fails the test loudly instead of hitting the real network.
// Installed msw is v3, which renamed this option from onUnhandledRequest
// (v2) to onUnhandledFrame.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
