import { type ReactNode, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { create } from 'zustand'
import { useBackClose, useEscape } from '@/app/hooks'
import { Icon, type IconName } from './Icon'

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

type BtnProps = React.ButtonHTMLAttributes<HTMLButtonElement> & { small?: boolean; block?: boolean }

export function PrimaryButton({ small, block, className = '', ...p }: BtnProps) {
  return <button type="button" {...p} className={`btn btn-primary ${small ? 'btn-small' : ''} ${block ? 'btn-block' : ''} ${className}`} />
}
export function SecondaryButton({ small, block, className = '', ...p }: BtnProps) {
  return <button type="button" {...p} className={`btn btn-secondary ${small ? 'btn-small' : ''} ${block ? 'btn-block' : ''} ${className}`} />
}
export function TextButton({ danger, className = '', ...p }: React.ButtonHTMLAttributes<HTMLButtonElement> & { danger?: boolean }) {
  return <button type="button" {...p} className={`btn-text ${danger ? 'danger' : ''} ${className}`} />
}

export function IconButton({
  icon,
  label,
  outlined,
  highlight,
  size = 24,
  ...p
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { icon: IconName; label: string; outlined?: boolean; highlight?: boolean; size?: number }) {
  return (
    <button type="button" aria-label={label} title={label} data-highlight={highlight} {...p} className={`icon-btn ${outlined ? 'icon-btn-outlined' : ''} ${p.className ?? ''}`}>
      <Icon name={icon} size={size} />
    </button>
  )
}

// ---------------------------------------------------------------------------
// Fields
// ---------------------------------------------------------------------------

interface FieldProps {
  label: string
  value: string
  onChange: (v: string) => void
  max?: number
  counter?: boolean
  multiline?: boolean
  rows?: number
  placeholder?: string
  type?: string
  inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode']
  autoFocus?: boolean
  autoComplete?: string
  onEnter?: () => void
  name?: string
}

export function Field({ label, value, onChange, max, counter, multiline, rows = 3, placeholder, type = 'text', inputMode, autoFocus, autoComplete, onEnter, name }: FieldProps) {
  const id = useId()
  const common = {
    id,
    name,
    value,
    placeholder,
    autoFocus,
    autoComplete,
    maxLength: max,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(e.target.value),
  }
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {multiline ? (
        <textarea {...common} rows={rows} />
      ) : (
        <input
          {...common}
          type={type}
          inputMode={inputMode}
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && onEnter) {
              e.preventDefault()
              onEnter()
            }
          }}
        />
      )}
      {counter && max ? <span className="counter">{`${value.length}/${max}`}</span> : null}
    </div>
  )
}

export function Switch({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return <button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled} className="switch" onClick={(e) => { e.stopPropagation(); onChange(!checked) }} />
}

export function Segmented<T extends string>({ value, options, onChange, label }: { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void; label: string }) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" aria-checked={o.value === value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Chips
// ---------------------------------------------------------------------------

export function CategoryDot({ color, large }: { color: string; large?: boolean }) {
  return <span className={`dot ${large ? 'dot-lg' : ''}`} style={{ background: color }} aria-hidden="true" />
}

export function CategoryChip({ label, color, selected, count, onClick, role = 'button' }: { label: string; color?: string; selected: boolean; count?: number; onClick: () => void; role?: 'button' | 'radio' }) {
  const pressed = role === 'radio' ? { role: 'radio', 'aria-checked': selected } : { 'aria-pressed': selected }
  return (
    <button type="button" className="chip" {...pressed} onClick={onClick}>
      {color ? <CategoryDot color={color} /> : null}
      <span>{label}</span>
      {count !== undefined ? <span className="count">{count}</span> : null}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

export function ScreenHeader({ title, count, children }: { title: string; count?: number; children?: ReactNode }) {
  return (
    <header className="screen-header">
      <h1 className="t-screen-title">{title}</h1>
      {count !== undefined ? <span className="count-pill">{count}</span> : null}
      <span className="spacer" />
      {children}
    </header>
  )
}

// ---------------------------------------------------------------------------
// Overlays
// ---------------------------------------------------------------------------

/** Moves focus into an overlay on open and restores it on close. */
function useFocusScope(ref: React.RefObject<HTMLElement | null>) {
  useLayoutEffect(() => {
    const prev = document.activeElement as HTMLElement | null
    const el = ref.current
    const first = el?.querySelector<HTMLElement>('[autofocus], input, textarea')
    ;(first ?? el)?.focus({ preventScroll: true })
    return () => prev?.focus?.({ preventScroll: true })
  }, [ref])
}

interface OverlayProps {
  onClose: () => void
  children: ReactNode
  label: string
  variant?: 'sheet' | 'dialog' | 'detail'
  /** Hide the drag handle (e.g. the hand-built detail sheet). */
  noHandle?: boolean
}

/** A modal surface: bottom sheet on phones, centred modal on desktop; a dialog is always centred. */
export function Overlay({ onClose, children, label, variant = 'sheet', noHandle }: OverlayProps) {
  const ref = useRef<HTMLDivElement>(null)
  useBackClose(true, onClose)
  useEscape(true, onClose)
  useFocusScope(ref)
  return createPortal(
    <div
      className={`overlay ${variant === 'dialog' ? 'dialog-overlay' : ''} ${variant === 'detail' ? 'detail' : ''}`}
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        tabIndex={-1}
        className={variant === 'dialog' ? 'dialog' : `sheet ${variant === 'detail' ? 'detail' : ''}`}
      >
        {variant === 'sheet' && !noHandle ? <div className="sheet-handle" /> : null}
        {children}
      </div>
    </div>,
    document.body,
  )
}

export const Sheet = (p: Omit<OverlayProps, 'variant'>) => <Overlay {...p} variant="sheet" />
export const Dialog = (p: Omit<OverlayProps, 'variant'>) => <Overlay {...p} variant="dialog" />

// ---------------------------------------------------------------------------
// Dropdown menu
// ---------------------------------------------------------------------------

export function Menu({ anchor, onClose, children, align = 'start' }: { anchor: HTMLElement | null; onClose: () => void; children: ReactNode; align?: 'start' | 'end' }) {
  const ref = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null)
  useEscape(true, onClose)
  useLayoutEffect(() => {
    if (!anchor || !ref.current) return
    const a = anchor.getBoundingClientRect()
    const m = ref.current.getBoundingClientRect()
    let left = align === 'end' ? a.right - m.width : a.left
    left = Math.max(8, Math.min(left, innerWidth - m.width - 8))
    let top = a.bottom + 4
    if (top + m.height > innerHeight - 8) top = Math.max(8, a.top - m.height - 4)
    setPos({ top, left })
  }, [anchor, align])
  useEffect(() => {
    const on = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node) && !anchor?.contains(e.target as Node)) onClose()
    }
    document.addEventListener('mousedown', on)
    return () => document.removeEventListener('mousedown', on)
  }, [anchor, onClose])
  return createPortal(
    <div ref={ref} className="menu" role="menu" style={{ top: pos?.top ?? -999, left: pos?.left ?? -999, visibility: pos ? 'visible' : 'hidden' }}>
      {children}
    </div>,
    document.body,
  )
}

// ---------------------------------------------------------------------------
// Snackbar (single slot, like the Scaffold's SnackbarHost)
// ---------------------------------------------------------------------------

interface SnackState {
  current: { id: number; message: string; action?: { label: string; run: () => void }; onTimeout?: () => void } | null
  show(message: string, opts?: { action?: { label: string; run: () => void }; onTimeout?: () => void; duration?: number }): void
  dismiss(runTimeout?: boolean): void
}

let snackId = 0
let snackTimer: ReturnType<typeof setTimeout> | undefined

export const useSnackbar = create<SnackState>((set, get) => ({
  current: null,
  show(message, opts = {}) {
    // A new message replaces the old one; the old one's undo window closes.
    get().dismiss(true)
    const id = ++snackId
    set({ current: { id, message, action: opts.action, onTimeout: opts.onTimeout } })
    clearTimeout(snackTimer)
    snackTimer = setTimeout(() => get().dismiss(true), opts.duration ?? (opts.action ? 4000 : 3000))
  },
  dismiss(runTimeout = false) {
    const cur = get().current
    if (!cur) return
    clearTimeout(snackTimer)
    set({ current: null })
    if (runTimeout) cur.onTimeout?.()
  },
}))

export function SnackbarHost() {
  const cur = useSnackbar((s) => s.current)
  if (!cur) return null
  return (
    <div className="snackbar-host" role="status" aria-live="polite">
      <div className="snackbar" key={cur.id}>
        <span className="msg">{cur.message}</span>
        {cur.action ? (
          <button
            type="button"
            onClick={() => {
              const run = cur.action!.run
              useSnackbar.getState().dismiss(false)
              run()
            }}
          >
            {cur.action.label}
          </button>
        ) : null}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

export function EmptyState({ title, body, action }: { title: string; body: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="art" aria-hidden="true">
        <i className="a" />
        <i className="b" />
        <i className="c">
          <Icon name="link" size={36} />
        </i>
      </div>
      <h2 className="t-empty-title">{title}</h2>
      <p>{body}</p>
      {action}
    </div>
  )
}
