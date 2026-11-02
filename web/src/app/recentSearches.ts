import { addRecentSearch } from '@/features/home/selectors'
import { usePrefs } from './prefs'

/** Remembers a submitted search (Enter, or opening one of its results). */
export function rememberSearch(query: string): void {
  const { recentSearches, set } = usePrefs.getState()
  const next = addRecentSearch(recentSearches, query)
  if (next !== recentSearches) set('recentSearches', next)
}

export const clearRecentSearches = (): void => usePrefs.getState().set('recentSearches', [])
