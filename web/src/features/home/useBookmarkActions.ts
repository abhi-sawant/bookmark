import { useCallback } from 'react'
import { type Bookmark } from '@/data/models'
import { openLink } from '@/app/actions'
import { toggleSelect, useUi } from '@/app/uiStore'

/** Shared tap / long-press handlers for any list of bookmarks. */
export function useBookmarkHandlers() {
  const set = useUi((s) => s.set)
  const selecting = useUi((s) => s.selecting)
  // In bulk-select mode both a tap and a long-press toggle the bookmark.
  const onOpen = useCallback((b: Bookmark) => (selecting ? toggleSelect(b.id) : openLink(b)), [selecting])
  const onActions = useCallback((b: Bookmark) => (selecting ? toggleSelect(b.id) : set('context', b.id)), [selecting, set])
  return { onOpen, onActions }
}
