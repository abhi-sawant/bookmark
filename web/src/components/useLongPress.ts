import { useRef } from 'react'

/**
 * Long-press (touch) and right-click (mouse) both fire [onTrigger]. Returns props to spread on
 * the element plus a `consumedClick` check so the click that ends a long-press doesn't also open the link.
 */
export function useLongPress(onTrigger: (e: { clientX: number; clientY: number }) => void, ms = 450) {
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const start = useRef<{ x: number; y: number } | null>(null)
  const fired = useRef(false)

  const cancel = () => {
    clearTimeout(timer.current)
    start.current = null
  }

  return {
    props: {
      onPointerDown(e: React.PointerEvent) {
        if (e.pointerType === 'mouse') return
        fired.current = false
        start.current = { x: e.clientX, y: e.clientY }
        const point = { clientX: e.clientX, clientY: e.clientY }
        timer.current = setTimeout(() => {
          fired.current = true
          navigator.vibrate?.(10)
          onTrigger(point)
        }, ms)
      },
      onPointerMove(e: React.PointerEvent) {
        const s = start.current
        if (s && Math.hypot(e.clientX - s.x, e.clientY - s.y) > 10) cancel()
      },
      onPointerUp: cancel,
      onPointerCancel: cancel,
      onPointerLeave: cancel,
      onContextMenu(e: React.MouseEvent) {
        e.preventDefault()
        if (!fired.current) onTrigger({ clientX: e.clientX, clientY: e.clientY })
        fired.current = true
      },
    },
    /** True (once) if the click following this gesture should be swallowed. */
    consumedClick(): boolean {
      const was = fired.current
      fired.current = false
      return was
    },
  }
}
