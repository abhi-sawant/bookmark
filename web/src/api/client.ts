import { useSession } from '@/account/session'

export const API_BASE: string = (import.meta.env.VITE_API_BASE as string | undefined) ?? 'https://api.bookmark.slowatcoding.com'

/** The server answered with an error body ({"error":{"code","message"}}). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

/** The request never reached the server (offline, DNS, CORS, ...). */
export class NetworkError extends Error {
  constructor() {
    super("Couldn't reach the server. Check your connection and try again.")
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  form?: FormData
  auth?: boolean
  query?: Record<string, string | number>
}

export async function apiRequest<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const url = new URL(`${API_BASE}/api/${path}`)
  for (const [k, v] of Object.entries(opts.query ?? {})) url.searchParams.set(k, String(v))

  const headers: Record<string, string> = {}
  if (opts.auth) {
    const token = useSession.getState().token
    if (token) headers.Authorization = `Bearer ${token}`
  }
  let body: BodyInit | undefined
  if (opts.form) body = opts.form
  else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }

  let res: Response
  try {
    res = await fetch(url, { method: opts.method ?? 'GET', headers, body })
  } catch {
    throw new NetworkError()
  }

  let json: unknown = null
  try {
    json = await res.json()
  } catch {
    // non-JSON body; handled below
  }
  if (!res.ok) {
    const err = (json as { error?: { code?: string; message?: string } } | null)?.error
    const apiError = new ApiError(res.status, err?.code ?? 'HTTP_ERROR', err?.message ?? `Request failed (${res.status}).`)
    // A revoked/expired token: end the session (a password reset or "sign out everywhere" does this).
    if (opts.auth && res.status === 401) useSession.getState().expire()
    throw apiError
  }
  return json as T
}
