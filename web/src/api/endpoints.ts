import { apiRequest } from './client'
import type { AuthResponse, DeviceDto, PullResponse, PushRequest, PushResponse } from './dto'

export const AccountApi = {
  login: (email: string, password: string, deviceName: string) =>
    apiRequest<AuthResponse>('login.php', { method: 'POST', body: { email, password, device_name: deviceName } }),
  register: (email: string, password: string, deviceName: string) =>
    apiRequest<AuthResponse>('register.php', { method: 'POST', body: { email, password, device_name: deviceName } }),
  forgotPassword: (email: string) =>
    apiRequest<{ ok: boolean; message: string }>('forgot_password.php', { method: 'POST', body: { email } }),
  logout: () => apiRequest<{ ok: boolean }>('logout.php', { method: 'POST', body: {}, auth: true }),
  logoutAll: () => apiRequest<{ ok: boolean; revoked_count: number }>('logout_all.php', { method: 'POST', body: {}, auth: true }),
  devices: () => apiRequest<DeviceDto[]>('devices.php', { auth: true }),
  revokeDevice: (deviceId: number) =>
    apiRequest<{ ok: boolean }>('devices.php', { method: 'POST', body: { device_id: deviceId }, query: { action: 'revoke' }, auth: true }),
}

export const SyncApi = {
  pull: (since: number, limit = 500) =>
    apiRequest<PullResponse>('sync/pull.php', { auth: true, query: { since, limit } }),
  push: (body: PushRequest) => apiRequest<PushResponse>('sync/push.php', { method: 'POST', body, auth: true }),
  uploadThumbnail: (bookmarkId: string, file: Blob) => {
    const form = new FormData()
    form.set('bookmark_id', bookmarkId)
    form.set('file', file, `${bookmarkId}.webp`)
    return apiRequest<{ url: string }>('thumbnails/upload.php', { method: 'POST', form, auth: true })
  },
}
