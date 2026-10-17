import '@testing-library/jest-dom/vitest'
import { vi } from 'vitest'

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
