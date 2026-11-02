import { useCallback, useEffect, useState } from 'react'
import { isValid, normalize } from '@/core/util/urlNormalizer'
import { extract } from '@/core/util/urlExtractor'
import { defaultCategoryId, findByUrl, saveBookmark, useData } from '@/data/repo'
import { usePrefs } from '@/app/prefs'
import { Icon } from '@/components/Icon'
import { useSnackbar } from '@/components/ui'

/**
 * Offers a copied link as a one-tap save. The clipboard is only read when the
 * browser already grants `clipboard-read` (no prompt), and only when the window
 * gains focus. Never saves on its own.
 */
export function ClipboardChip() {
  const enabled = usePrefs((s) => s.clipboardChip)
  const loaded = useData((s) => s.loaded)
  const toast = useSnackbar((s) => s.show)
  const [url, setUrl] = useState<string | null>(null)
  // The last link dismissed or saved, so the chip does not come straight back.
  const [handled, setHandled] = useState<string | null>(null)

  const check = useCallback(async () => {
    if (!enabled || !loaded) {
      setUrl(null)
      return
    }
    try {
      const perm = await navigator.permissions.query({ name: 'clipboard-read' as PermissionName })
      if (perm.state !== 'granted') return
      const found = extract(await navigator.clipboard.readText()).primaryUrl
      const normalized = found && isValid(found) ? normalize(found) : null
      setUrl(found && normalized && normalized !== handled && !findByUrl(normalized) ? found : null)
    } catch {
      // not supported / not allowed: the Add sheet's Paste button still works
    }
  }, [enabled, loaded, handled])

  useEffect(() => {
    void check()
    const onFocus = () => void check()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [check])

  if (!url) return null

  function dismiss() {
    setHandled(normalize(url!))
    setUrl(null)
  }

  async function save() {
    const prefs = usePrefs.getState()
    const categories = useData.getState().categories
    const last = prefs.lastUsedCategory
    const categoryId = last && categories.some((c) => c.id === last) ? last : defaultCategoryId()
    const target = url!
    dismiss()
    const result = await saveBookmark({ rawUrl: target, categoryId })
    toast(result.kind === 'saved' ? 'Saved' : 'Already saved')
  }

  return (
    <div className="clip-chip" role="region" aria-label="Copied link">
      <Icon name="link" size={18} />
      <div className="clip-chip-text">
        <div className="t-row-title">Save copied link?</div>
        <div className="t-site clamp-1">{url}</div>
      </div>
      <button type="button" className="clip-chip-save" onClick={() => void save()}>Save</button>
      <button type="button" className="clip-chip-x" aria-label="Dismiss" onClick={dismiss}>
        <Icon name="close" size={18} />
      </button>
    </div>
  )
}
