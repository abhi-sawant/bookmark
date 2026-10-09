import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@/theme/tokens.css'
import '@/theme/base.css'
import '@/components/components.css'
import '@/features/home/home.css'
import '@/app/shell.css'
import { App } from '@/app/App'
import { initData } from '@/data/repo'
import { loadSyncStatus, startSyncScheduler } from '@/sync/engine'
import { startThemeSync } from '@/app/prefs'

startThemeSync()

void initData().then(() => {
  void loadSyncStatus()
  startSyncScheduler()
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
