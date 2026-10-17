import { getKeycloak } from '@/lib/auth'
import type { ProblemDetails } from './types'

/** Thrown by `fetchJson` for any non-2xx response, carrying the RFC 9457 Problem Details body. */
export class ApiError extends Error {
  status: number
  problem: ProblemDetails

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? problem.title)
    this.status = status
    this.problem = problem
  }
}

/**
 * Fetches `path` as JSON, attaching the current Keycloak Bearer token.
 * Throws `ApiError` on a non-2xx response.
 */
export async function fetchJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getKeycloak().token
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...options, headers })

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({
      type: 'about:blank',
      title: response.statusText,
      status: response.status,
    }))) as ProblemDetails
    throw new ApiError(response.status, problem)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
