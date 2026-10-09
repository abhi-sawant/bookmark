import { useEffect, useRef } from 'react'
import { useUi } from '@/app/uiStore'
import { Icon } from '@/components/Icon'

/** Header search on desktop: filters Home in place instead of opening a separate screen. */
export function DesktopSearchBox() {
  const query = useUi((s) => s.searchQuery)
  const set = useUi((s) => s.set)
  const ref = useRef<HTMLInputElement>(null)

  // "/" focuses search from anywhere (unless already typing in a field).
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement
      if (e.key === '/' && !/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) && !t.isContentEditable) {
        e.preventDefault()
        ref.current?.focus()
      }
    }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [])

  return (
    <div className="desktop-search panel">
      <Icon name="search" size={18} />
      <input
        ref={ref}
        type="search"
        value={query}
        onChange={(e) => set('searchQuery', e.target.value)}
        onKeyDown={(e) => e.key === 'Escape' && (set('searchQuery', ''), ref.current?.blur())}
        placeholder="Search bookmarks"
        aria-label="Search bookmarks"
        aria-keyshortcuts="/"
      />
      {query ? (
        <button type="button" aria-label="Clear search" onClick={() => set('searchQuery', '')}>
          <Icon name="closeFilled" size={16} />
        </button>
      ) : (
        <kbd>/</kbd>
      )}
    </div>
  )
}
