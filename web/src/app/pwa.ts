import { useEffect } from 'react'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { useSnackbar } from '@/components/ui'

/** Replaces Android's GitHub update check: when a new build is ready, offer a one-tap reload. */
export function useUpdatePrompt(): void {
  const { needRefresh, updateServiceWorker } = useRegisterSW({ onRegisterError: (e) => console.warn('[pwa] sw error', e) })
  const show = useSnackbar((s) => s.show)
  useEffect(() => {
    if (needRefresh[0]) show('A new version is available', { action: { label: 'Reload', run: () => void updateServiceWorker(true) }, duration: 12000 })
  }, [needRefresh, show, updateServiceWorker])
}
