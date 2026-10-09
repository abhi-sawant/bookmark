import { create } from 'zustand'

const KEY = 'bookmark.session'

interface Stored {
  token: string
  email: string
  deviceId: number
}

interface SessionState {
  token: string | null
  email: string | null
  deviceId: number | null
  /** Set when the server rejected our token, so the UI can say why we signed out. */
  expired: boolean
  setSession(s: Stored): void
  clear(): void
  expire(): void
  dismissExpired(): void
}

function load(): Partial<Stored> {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Stored) : {}
  } catch {
    return {}
  }
}

function save(s: Stored | null) {
  try {
    if (s) localStorage.setItem(KEY, JSON.stringify(s))
    else localStorage.removeItem(KEY)
  } catch {
    // storage unavailable (private mode); the session just won't survive a reload
  }
}

const initial = load()

export const useSession = create<SessionState>((set) => ({
  token: initial.token ?? null,
  email: initial.email ?? null,
  deviceId: initial.deviceId ?? null,
  expired: false,
  setSession(s) {
    save(s)
    set({ ...s, expired: false })
  },
  clear() {
    save(null)
    set({ token: null, email: null, deviceId: null })
  },
  expire() {
    save(null)
    set({ token: null, email: null, deviceId: null, expired: true })
  },
  dismissExpired: () => set({ expired: false }),
}))

export const isSignedIn = (): boolean => useSession.getState().token !== null
