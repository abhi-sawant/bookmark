import { type ReactNode, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSession } from '@/account/session'
import { signOut } from '@/account/service'
import { BackupFormatError, backupFileName, buildBackup, downloadBlob, parseBackup } from '@/data/backup'
import { useData } from '@/data/repo'
import { relativeTime, plural } from '@/app/format'
import { usePrefs } from '@/app/prefs'
import { useUi } from '@/app/uiStore'
import { Icon, type IconName } from '@/components/Icon'
import { Dialog, PrimaryButton, ScreenHeader, Segmented, Switch, TextButton, useSnackbar } from '@/components/ui'
import { syncNow, useSyncStatus } from '@/sync/engine'
import { DevicesDialog } from './DevicesDialog'

function Group({ children }: { children: ReactNode }) {
  return <div className="settings-group">{children}</div>
}
function Section({ title }: { title: string }) {
  return <div className="section-header t-section">{title}</div>
}
function Row({ icon, title, sub, onClick, danger, disabled, trailing }: { icon?: IconName; title: string; sub?: string; onClick?: () => void; danger?: boolean; disabled?: boolean; trailing?: ReactNode }) {
  const body = (
    <>
      {icon ? (
        <span className="lead">
          <Icon name={icon} size={20} />
        </span>
      ) : null}
      <span className="body">
        <span className="t-row-title" style={{ display: 'block' }}>{title}</span>
        {sub ? <span className="t-row-sub sub" style={{ display: 'block' }}>{sub}</span> : null}
      </span>
      {trailing}
    </>
  )
  if (!onClick) return <div className="settings-row">{body}</div>
  // A row that hosts its own control (a switch) cannot itself be a <button>.
  if (trailing) {
    return (
      <div className="settings-row clickable" onClick={onClick}>
        {body}
      </div>
    )
  }
  return (
    <button type="button" className={`settings-row ${danger ? 'danger' : ''}`} aria-disabled={disabled} onClick={disabled ? undefined : onClick}>
      {body}
    </button>
  )
}

export function SettingsScreen() {
  const navigate = useNavigate()
  const email = useSession((s) => s.email)
  const bookmarks = useData((s) => s.bookmarks)
  const categories = useData((s) => s.categories)
  const { syncing, lastSyncedAt, error } = useSyncStatus()
  const { theme, trueBlack, set: setPref } = usePrefs()
  const toast = useSnackbar((s) => s.show)
  const setUi = useUi((s) => s.set)
  const fileInput = useRef<HTMLInputElement>(null)
  const [confirmSignOut, setConfirmSignOut] = useState(false)
  const [devices, setDevices] = useState(false)
  const [signingOut, setSigningOut] = useState(false)

  function exportBackup() {
    try {
      downloadBlob(buildBackup(bookmarks, categories), backupFileName())
      toast(`Exported ${plural(bookmarks.length, 'bookmark')} and ${plural(categories.length, 'category', 'categories')}.`)
    } catch {
      toast("Couldn't export a backup.")
    }
  }

  async function onPickBackup(file: File | undefined) {
    if (!file) return
    try {
      const bytes = new Uint8Array(await file.arrayBuffer())
      parseBackup(bytes) // validate before showing the preview
      setUi('importPreview', { fileName: file.name, bytes })
    } catch (e) {
      toast(e instanceof BackupFormatError ? e.message : "Couldn't import this backup.")
    }
  }

  return (
    <div className="screen">
      <ScreenHeader title="settings" />

      <Section title="sync" />
      <Group>
        {email ? (
          <>
            <Row icon="sync" title={email} sub={error ?? (syncing ? 'Syncing…' : lastSyncedAt ? `Last synced ${relativeTime(lastSyncedAt)}` : 'Not synced yet')} />
            <Row title="Sync now" onClick={() => void syncNow()} disabled={syncing} />
            <Row title="Devices" onClick={() => setDevices(true)} />
            <Row title="Sign out" danger onClick={() => setConfirmSignOut(true)} />
          </>
        ) : (
          <Row icon="sync" title="Sign in to sync across devices" sub="Bookmarks and categories stay in sync. Fully optional." onClick={() => navigate('/account')} />
        )}
      </Group>

      <Section title="backup" />
      <Group>
        <Row icon="fileDownload" title="Export backup" sub={`${plural(bookmarks.length, 'bookmark')} · ${plural(categories.length, 'category', 'categories')}`} onClick={exportBackup} />
        <Row icon="fileUpload" title="Import backup" sub="Merge or replace, with a preview first" onClick={() => fileInput.current?.click()} />
      </Group>
      <input ref={fileInput} type="file" accept=".zip,application/zip" hidden onChange={(e) => { void onPickBackup(e.target.files?.[0]); e.target.value = '' }} />

      <Section title="appearance" />
      <Group>
        <div className="settings-row" style={{ display: 'block' }}>
          <div className="t-row-title" style={{ marginBottom: 10 }}>Theme</div>
          <Segmented label="Theme" value={theme} onChange={(v) => setPref('theme', v)} options={[{ value: 'SYSTEM', label: 'System' }, { value: 'LIGHT', label: 'Light' }, { value: 'DARK', label: 'Dark' }]} />
        </div>
        <Row title="True black for OLED" trailing={<Switch label="True black for OLED" checked={trueBlack} onChange={(v) => setPref('trueBlack', v)} />} onClick={() => setPref('trueBlack', !trueBlack)} />
      </Group>

      <p className="t-row-sub muted" style={{ padding: '20px 26px 0' }}>
        Link previews aren&apos;t fetched on the web. Bookmarks you add here show a placeholder until the Android app fetches a preview after syncing.
      </p>

      {confirmSignOut ? (
        <Dialog label="Sign out" onClose={() => setConfirmSignOut(false)}>
          <h2 className="t-sheet-title">Sign out?</h2>
          <p className="t-row-sub muted" style={{ margin: 0, fontSize: 14, lineHeight: '20px' }}>
            Your bookmarks stay in your account. This browser&apos;s copy will be removed after a final sync.
          </p>
          <div className="dialog-actions">
            <TextButton onClick={() => setConfirmSignOut(false)}>Cancel</TextButton>
            <PrimaryButton small disabled={signingOut} style={{ background: 'var(--error)' }} onClick={() => { setSigningOut(true); void signOut().finally(() => { setSigningOut(false); setConfirmSignOut(false) }) }}>Sign out</PrimaryButton>
          </div>
        </Dialog>
      ) : null}
      {devices ? <DevicesDialog onClose={() => setDevices(false)} /> : null}
    </div>
  )
}
