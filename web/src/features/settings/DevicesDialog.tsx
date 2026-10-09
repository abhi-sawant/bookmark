import { useEffect, useState } from 'react'
import { AccountApi } from '@/api/endpoints'
import type { DeviceDto } from '@/api/dto'
import { relativeTime } from '@/app/format'
import { Dialog, TextButton, useSnackbar } from '@/components/ui'

export function DevicesDialog({ onClose }: { onClose: () => void }) {
  const [devices, setDevices] = useState<DeviceDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const toast = useSnackbar((s) => s.show)

  const load = () => AccountApi.devices().then(setDevices).catch((e: Error) => setError(e.message))
  useEffect(() => void load(), [])

  async function revoke(d: DeviceDto) {
    try {
      await AccountApi.revokeDevice(d.device_id)
      toast(`Signed out ${d.device_name}`)
      await load()
    } catch (e) {
      toast((e as Error).message)
    }
  }

  return (
    <Dialog label="Devices" onClose={onClose}>
      <h2 className="t-sheet-title">Devices</h2>
      {error ? <p className="field-error">{error}</p> : null}
      {!devices && !error ? <div className="skeleton skeleton-line" style={{ height: 40 }} /> : null}
      <div className="device-list">
        {devices?.map((d) => (
          <div key={d.device_id} className="device-row">
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="t-row-title clamp-1">{d.device_name}{d.is_current ? ' (this browser)' : ''}</div>
              <div className="t-row-sub muted">{d.last_used_at ? `Active ${relativeTime(d.last_used_at)}` : 'Never used'}</div>
            </div>
            {!d.is_current ? <TextButton danger onClick={() => void revoke(d)}>Sign out</TextButton> : null}
          </div>
        ))}
      </div>
      <div className="dialog-actions">
        <TextButton onClick={onClose}>Close</TextButton>
      </div>
    </Dialog>
  )
}
