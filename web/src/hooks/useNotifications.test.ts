import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { toast } from 'sonner'
import { useNotifications } from './useNotifications'

// sonner's `toast` export isn't configurable under Vitest's ESM mocking, so
// vi.spyOn on the live module throws; mock the module instead.
vi.mock('sonner', () => ({ toast: vi.fn() }))

class MockWebSocket {
  static instances: MockWebSocket[] = []
  url: string
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: (() => void) | null = null
  closed = false
  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }
  close() {
    this.closed = true
  }
}

describe('useNotifications', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    vi.stubGlobal('WebSocket', MockWebSocket)
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('opens a WebSocket connection to the notifications endpoint with the auth token', () => {
    renderHook(() => useNotifications('test-token'))

    expect(MockWebSocket.instances).toHaveLength(1)
    expect(MockWebSocket.instances[0].url).toContain('token=test-token')
  })

  it('shows a toast with the message when a notification arrives', () => {
    renderHook(() => useNotifications('test-token'))
    const ws = MockWebSocket.instances[0]
    // Real wire shape per notifications/src/domain.rs Notification
    // (#[serde(rename_all = "camelCase")]): id/eventType/transactionId/
    // message/timestamp, not just `{ message }`.
    ws.onmessage?.({
      data: JSON.stringify({
        id: 'notif-1',
        eventType: 'TransactionUpdate',
        transactionId: 'tx-1',
        message: 'Transfer completed',
        timestamp: 1700000000,
      }),
    } as MessageEvent)

    expect(toast).toHaveBeenCalledWith('Transfer completed')
  })

  it('closes the socket on unmount', () => {
    const { unmount } = renderHook(() => useNotifications('test-token'))
    const ws = MockWebSocket.instances[0]

    unmount()

    expect(ws.closed).toBe(true)
  })

  it('reconnects after the socket closes unexpectedly', () => {
    vi.useFakeTimers()

    renderHook(() => useNotifications('test-token'))
    expect(MockWebSocket.instances).toHaveLength(1)

    MockWebSocket.instances[0].onclose?.()
    vi.runOnlyPendingTimers()

    expect(MockWebSocket.instances).toHaveLength(2)
  })
})
