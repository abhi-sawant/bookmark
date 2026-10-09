import { create } from 'zustand'
import type { SortOrder, ThemeMode, ViewMode } from '@/data/models'

/** Device-local preferences (nothing here is synced, same as Android's DataStore). */
interface Prefs {
  theme: ThemeMode
  trueBlack: boolean
  viewMode: ViewMode
  sortOrder: SortOrder
  lastUsedCategory: string | null
  set<K extends keyof Omit<Prefs, 'set'>>(key: K, value: Prefs[K]): void
}

const KEY = 'bookmark.prefs'

function load(): Partial<Prefs> {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Prefs>
  } catch {
    return {}
  }
}

const stored = load()

export const usePrefs = create<Prefs>((set, get) => ({
  theme: stored.theme ?? 'SYSTEM',
  trueBlack: stored.trueBlack ?? false,
  viewMode: stored.viewMode ?? 'GRID',
  sortOrder: stored.sortOrder ?? 'NEWEST',
  lastUsedCategory: stored.lastUsedCategory ?? null,
  set(key, value) {
    set({ [key]: value } as Partial<Prefs>)
    const { theme, trueBlack, viewMode, sortOrder, lastUsedCategory } = get()
    try {
      localStorage.setItem(KEY, JSON.stringify({ theme, trueBlack, viewMode, sortOrder, lastUsedCategory }))
    } catch {
      // storage unavailable; preferences just won't persist
    }
  },
}))

const THEME_COLOR = {
  light: '#f2f3f4',
  dark: '#0b0d0e',
  black: '#000000',
}

/** Applies the theme to <html> and keeps the browser chrome colour in step. */
export function applyTheme(): void {
  const { theme, trueBlack } = usePrefs.getState()
  const dark = theme === 'DARK' || (theme === 'SYSTEM' && matchMedia('(prefers-color-scheme: dark)').matches)
  const root = document.documentElement
  root.dataset.theme = dark ? 'dark' : 'light'
  root.dataset.black = String(dark && trueBlack)
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', dark ? (trueBlack ? THEME_COLOR.black : THEME_COLOR.dark) : THEME_COLOR.light)
}

export function startThemeSync(): () => void {
  applyTheme()
  const mq = matchMedia('(prefers-color-scheme: dark)')
  mq.addEventListener('change', applyTheme)
  const unsub = usePrefs.subscribe(applyTheme)
  return () => {
    mq.removeEventListener('change', applyTheme)
    unsub()
  }
}
