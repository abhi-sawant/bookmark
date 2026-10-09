import { create } from 'zustand'

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
  addEdit: AddEditState | null
  context: string | null
  detail: string | null
  duplicate: string | null
  categoryDialog: { id?: string } | null
  deleteCategoryId: string | null
  importPreview: { fileName: string; bytes: Uint8Array } | null
  set<K extends keyof Omit<UiState, 'set'>>(key: K, value: UiState[K]): void
}

export const useUi = create<UiState>((set) => ({
  filterCategory: null,
  searchQuery: '',
  addEdit: null,
  context: null,
  detail: null,
  duplicate: null,
  categoryDialog: null,
  deleteCategoryId: null,
  importPreview: null,
  set: (key, value) => set({ [key]: value } as Partial<UiState>),
}))

export const openAdd = (prefill?: AddEditState['prefill']) => useUi.getState().set('addEdit', { prefill })
export const openEdit = (editId: string) => useUi.getState().set('addEdit', { editId })
