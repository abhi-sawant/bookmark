import { useMemo, useState } from 'react'
import { importBackup, parseBackup, previewImport } from '@/data/backup'
import { useUi } from '@/app/uiStore'
import { PrimaryButton, SecondaryButton, Segmented, Sheet, useSnackbar } from '@/components/ui'

export function ImportPreviewSheet({ fileName, bytes }: { fileName: string; bytes: Uint8Array }) {
  const close = () => useUi.getState().set('importPreview', null)
  const toast = useSnackbar((s) => s.show)
  const parsed = useMemo(() => parseBackup(bytes), [bytes])
  const preview = useMemo(() => previewImport(parsed), [parsed])
  const [mode, setMode] = useState<'MERGE' | 'REPLACE'>('MERGE')
  const [busy, setBusy] = useState(false)

  async function run() {
    setBusy(true)
    try {
      await importBackup(parsed, mode)
      toast('Import complete.')
      close()
    } catch (e) {
      console.warn('[import] failed', e)
      toast("Couldn't import this backup.")
    } finally {
      setBusy(false)
    }
  }

  const rows: [string, number][] = [
    ['Bookmarks in file', preview.total],
    ['New to this device', preview.newCount],
    ['Already saved', preview.alreadySaved],
    ['Categories', preview.categories],
  ]
  return (
    <Sheet onClose={close} label="Import backup">
      <h2 className="t-sheet-title sheet-title" style={{ marginBottom: 4 }}>Import backup</h2>
      <div className="t-caption muted" style={{ marginBottom: 14, overflowWrap: 'anywhere' }}>{fileName}</div>
      <div className="settings-group" style={{ margin: 0 }}>
        {rows.map(([label, n]) => (
          <div key={label} className="settings-row" style={{ padding: '13px 16px', justifyContent: 'space-between' }}>
            <span className="t-row-title">{label}</span>
            <span className="t-caption muted" style={{ fontSize: 12 }}>{n}</span>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 16 }}>
        <Segmented label="Import mode" value={mode} onChange={setMode} options={[{ value: 'MERGE', label: 'Merge' }, { value: 'REPLACE', label: 'Replace everything' }]} />
      </div>
      <div className="sheet-actions">
        <SecondaryButton onClick={close}>Cancel</SecondaryButton>
        <PrimaryButton disabled={busy} onClick={() => void run()}>Import</PrimaryButton>
      </div>
    </Sheet>
  )
}
