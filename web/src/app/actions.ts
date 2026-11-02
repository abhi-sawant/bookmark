import { type Bookmark } from '@/data/models'
import { deleteBookmark, setPinned, setUnread } from '@/data/repo'
import { useSnackbar } from '@/components/ui'

/** Opens the link in a new tab and clears its unread dot. */
export function openLink(b: Bookmark): void {
  if (b.isUnread) void setUnread(b.id, false)
  window.open(b.url, '_blank', 'noopener,noreferrer')
}

export async function copyLink(b: Bookmark): Promise<void> {
  try {
    await navigator.clipboard.writeText(b.url)
    useSnackbar.getState().show('Link copied')
  } catch {
    useSnackbar.getState().show("Couldn't copy the link")
  }
}

export async function shareLink(b: Bookmark): Promise<void> {
  if (navigator.share) {
    try {
      await navigator.share({ title: b.title, url: b.url })
    } catch {
      // dismissed by the user
    }
  } else {
    await copyLink(b)
  }
}

export const togglePin = (b: Bookmark) => setPinned(b.id, !b.isPinned)
export const toggleUnread = (b: Bookmark) => setUnread(b.id, !b.isUnread)

/** Moves to Trash (no confirm) with an Undo snackbar; it stays in Trash for 30 days. */
export async function deleteWithUndo(b: Bookmark): Promise<void> {
  const res = await deleteBookmark(b.id)
  if (!res) return
  const { undoable } = res
  useSnackbar.getState().show(`Moved “${b.title.slice(0, 30)}” to Trash`, {
    action: { label: 'Undo', run: () => void undoable.undo() },
    onTimeout: () => void undoable.commit(),
  })
}
