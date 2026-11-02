import { SHORTCUTS } from '@/app/shortcutList'
import { useUi } from '@/app/uiStore'
import { Sheet } from '@/components/ui'

/** Cheat-sheet of the desktop keyboard shortcuts, opened with `?` or from Settings. */
export function ShortcutsSheet() {
  const close = () => useUi.getState().set('shortcuts', false)
  return (
    <Sheet onClose={close} label="Keyboard shortcuts">
      <h2 className="t-sheet-title sheet-title">Keyboard shortcuts</h2>
      <div role="list" className="shortcut-list">
        {SHORTCUTS.map((s) => (
          <div role="listitem" key={s.keys.join('+')} className="shortcut-row">
            <span className="t-row-title">{s.action}</span>
            <span className="keys">
              {s.keys.map((k, i) => (
                <span key={k}>
                  {i > 0 ? <span className="muted"> then </span> : null}
                  <kbd>{k}</kbd>
                </span>
              ))}
            </span>
          </div>
        ))}
      </div>
    </Sheet>
  )
}
