import { AccountApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import { getMeta, setMeta } from '@/data/db'
import { clearAllData, flushPendingDeletes } from '@/data/repo'
import { syncNow } from '@/sync/engine'
import { useSession } from './session'

/** "Web (Chrome on macOS)" -- shown in the device list so a session can be recognised and revoked. */
export function deviceName(ua = navigator.userAgent): string {
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS X/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'unknown OS'
  return `Web (${browser} on ${os})`
}

async function establish(res: { token: string; device_id: number }, email: string): Promise<void> {
  // A different account than last time on this browser: its data must not leak into the new one.
  const previous = await getMeta<string>('last_account_email')
  if (previous && previous !== email) await clearAllData()
  await setMeta('last_account_email', email)
  useSession.getState().setSession({ token: res.token, email, deviceId: res.device_id })
}

export async function signIn(email: string, password: string): Promise<void> {
  const e = email.trim().toLowerCase()
  await establish(await AccountApi.login(e, password, deviceName()), e)
}

export async function register(email: string, password: string): Promise<void> {
  const e = email.trim().toLowerCase()
  await establish(await AccountApi.register(e, password, deviceName()), e)
}

/**
 * Signs out and removes this browser's copy of the data (it is a shared, long-lived store, unlike a
 * phone). A final sync runs first so nothing unsynced is lost.
 */
export async function signOut(): Promise<void> {
  await flushPendingDeletes()
  await syncNow()
  try {
    await AccountApi.logout()
  } catch (e) {
    if (!(e instanceof ApiError)) console.warn('[account] logout failed', e)
  }
  useSession.getState().clear()
  await clearAllData()
}

export const isValidEmail = (s: string): boolean => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s.trim())
