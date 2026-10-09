import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { extract } from '@/core/util/urlExtractor'
import { useIsDesktop } from './hooks'
import { openAdd, useUi } from './uiStore'

const isTyping = (t: EventTarget | null): boolean => {
  const el = t as HTMLElement | null
  return !!el && (/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) || el.isContentEditable)
}

/** Desktop conveniences: `n` new bookmark, `g` then `h`/`c`/`s` to navigate, and dropping a link onto the window. */
export function useGlobalShortcuts(): void {
  const navigate = useNavigate()
  const desktop = useIsDesktop()

  useEffect(() => {
    if (!desktop) return
    let chord: ReturnType<typeof setTimeout> | undefined
    let armed = false
    const onKey = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey || isTyping(e.target)) return
      const { addEdit, context, detail, duplicate, categoryDialog, deleteCategoryId, importPreview } = useUi.getState()
      if (addEdit || context || detail || duplicate || categoryDialog || deleteCategoryId || importPreview) return
      if (armed) {
        armed = false
        clearTimeout(chord)
        const to = { h: '/', c: '/categories', s: '/settings' }[e.key.toLowerCase()]
        if (to) {
          e.preventDefault()
          navigate(to)
        }
        return
      }
      if (e.key === 'n') {
        e.preventDefault()
        openAdd()
      } else if (e.key === 'g') {
        armed = true
        chord = setTimeout(() => (armed = false), 1200)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      clearTimeout(chord)
    }
  }, [desktop, navigate])

  // Drag a link (or text containing one) from another tab onto the window to add it.
  useEffect(() => {
    const over = (e: DragEvent) => {
      if (e.dataTransfer && Array.from(e.dataTransfer.types).some((t) => t === 'text/uri-list' || t === 'text/plain')) e.preventDefault()
    }
    const drop = (e: DragEvent) => {
      if (!e.dataTransfer || isTyping(e.target)) return
      const text = e.dataTransfer.getData('text/uri-list') || e.dataTransfer.getData('text/plain')
      const url = extract(text).primaryUrl
      if (url) {
        e.preventDefault()
        openAdd({ url })
      }
    }
    window.addEventListener('dragover', over)
    window.addEventListener('drop', drop)
    return () => {
      window.removeEventListener('dragover', over)
      window.removeEventListener('drop', drop)
    }
  }, [])
}
