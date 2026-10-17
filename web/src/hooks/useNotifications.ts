import { useEffect } from 'react'
import { toast } from 'sonner'
import type { Notification } from '@/lib/api/types'

// The server has no backoff of its own; a fixed delay is enough to recover
// from a dropped connection at a demo's notification volume.
const RECONNECT_DELAY_MS = 3000

/**
 * Opens a WebSocket to the Notifications service and toasts each incoming
 * notification. Reconnects after a fixed delay if the socket drops, and
 * closes it on unmount or when `token` changes/clears.
 */
export function useNotifications(token: string | undefined) {
  useEffect(() => {
    if (!token) {
      return
    }

    let socket: WebSocket
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined
    let stopped = false

    function connect() {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
      socket = new WebSocket(`${protocol}://${window.location.host}/api/notifications/ws?token=${token}`)

      socket.onmessage = (event) => {
        const notification = JSON.parse(event.data) as Notification
        toast(notification.message)
      }

      socket.onclose = () => {
        if (!stopped) {
          reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
        }
      }
    }

    connect()

    return () => {
      stopped = true
      clearTimeout(reconnectTimer)
      socket.close()
    }
  }, [token])
}
