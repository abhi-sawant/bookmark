import { create } from 'zustand'
import { NO_FILTERS, type SearchFilters } from '@/features/home/selectors'

export interface AddEditState {
  /** Present when editing an existing bookmark. */
  editId?: string
  /** Pre-filled values (share target, dropped link, pasted text). */
  prefill?: { url?: string; title?: string }
}

interface UiState {
  /** Home/Search category filter; null is "All". In memory only, like Android. */
  filterCategory: string | null
  searchQuery: string
  /** Search chips (Pinned, No category, This week). In memory only. */
  searchFilters: SearchFilters
  /** Bulk-select mode: tapping a bookmark toggles it instead of opening it. */
  selecting: boolean
  selectedIds: string[]
  addEdit: AddEditState | null
  context: string | null
  detail: string | null
  duplicate: string | null
  categoryDialog: { id?: string } | null
  deleteCategoryId: string | null
  importPreview: { fileName: string; bytes: Uint8Array } | null
  /** The keyboard-shortcuts cheat-sheet (`?`). */
  shortcuts: boolean
  set<K extends keyof Omit<UiState, 'set'>>(key: K, value: UiState[K]): void
}

export const useUi = create<UiState>((set) => ({
  filterCategory: null,
  searchQuery: '',
  searchFilters: NO_FILTERS,
  selecting: false,
  selectedIds: [],
  addEdit: null,
  context: null,
  detail: null,
  duplicate: null,
  categoryDialog: null,
  deleteCategoryId: null,
  importPreview: null,
  shortcuts: false,
  set: (key, value) => set({ [key]: value } as Partial<UiState>),
}))

export const openAdd = (prefill?: AddEditState['prefill']) => useUi.getState().set('addEdit', { prefill })
export const openEdit = (editId: string) => useUi.getState().set('addEdit', { editId })

/** Enters bulk-select mode with [id] already selected. */
export const startSelect = (id: string) => useUi.setState({ selecting: true, selectedIds: [id] })
export const clearSelect = () => useUi.setState({ selecting: false, selectedIds: [] })
export const toggleSelect = (id: string) =>
  useUi.setState((s) => {
    const selectedIds = s.selectedIds.includes(id) ? s.selectedIds.filter((x) => x !== id) : [...s.selectedIds, id]
    return { selectedIds }
  })
