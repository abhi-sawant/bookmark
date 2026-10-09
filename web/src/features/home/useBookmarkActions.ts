import { useCallback } from 'react'
import { type Bookmark } from '@/data/models'
import { openLink } from '@/app/actions'
import { useUi } from '@/app/uiStore'

/** Shared tap / long-press handlers for any list of bookmarks. */
export function useBookmarkHandlers() {
  const set = useUi((s) => s.set)
  const onOpen = useCallback((b: Bookmark) => openLink(b), [])
  const onActions = useCallback((b: Bookmark) => set('context', b.id), [set])
  return { onOpen, onActions }
}
