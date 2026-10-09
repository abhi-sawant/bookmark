import { useEffect, useLayoutEffect, useRef, useState } from 'react'

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => matchMedia(query).matches)
  useEffect(() => {
    const mq = matchMedia(query)
    const on = () => setMatches(mq.matches)
    on()
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [query])
  return matches
}

/** Desktop layout breakpoint: sidebar instead of bottom bar. */
export const useIsDesktop = () => useMediaQuery('(min-width: 900px)')

interface BackEntry {
  pushed: { current: boolean }
  close: () => void
}
const backStack: BackEntry[] = []
/** popstate events we caused ourselves with history.back(); they must not close anything. */
let ignorePops = 0

if (typeof window !== 'undefined') {
  window.addEventListener('popstate', () => {
    if (ignorePops > 0) {
      ignorePops--
      return
    }
    // A real Back press closes only the top-most overlay.
    const top = backStack[backStack.length - 1]
    if (top) {
      top.pushed.current = false
      top.close()
    }
  })
}

/**
 * Makes the browser/hardware Back button close an open overlay instead of leaving the app:
 * a history entry is pushed while it is open and dropped when it closes.
 */
export function useBackClose(open: boolean, onClose: () => void): void {
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  // Refs survive React StrictMode's simulated unmount/remount, which is what keeps this from
  // pushing twice or immediately navigating back (and so closing) on mount.
  const pushed = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    if (!open) return
    mounted.current = true
    if (!pushed.current) {
      history.pushState({ overlay: true }, '')
      pushed.current = true
    }
    const entry: BackEntry = { pushed, close: () => closeRef.current() }
    backStack.push(entry)
    return () => {
      mounted.current = false
      backStack.splice(backStack.indexOf(entry), 1)
      // Closed some other way (Cancel, Save, scrim): drop the entry we pushed -- unless this was
      // only StrictMode's remount, in which case the effect has already run again by the next tick.
      setTimeout(() => {
        if (!mounted.current && pushed.current) {
          pushed.current = false
          ignorePops++
          history.back()
        }
      }, 0)
    }
  }, [open])
}

export function useEscape(active: boolean, onEscape: () => void): void {
  const ref = useRef(onEscape)
  ref.current = onEscape
  useEffect(() => {
    if (!active) return
    const on = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        ref.current()
      }
    }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [active])
}

/** Debounced copy of a value. */
export function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

export function useElementWidth<T extends HTMLElement>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T>(null)
  const [w, setW] = useState(0)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    setW(el.clientWidth)
    const ro = new ResizeObserver(() => setW(el.clientWidth))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [ref, w]
}
