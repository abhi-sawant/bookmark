import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useSession } from '@/account/session'
import { extract } from '@/core/util/urlExtractor'
import { AccountScreen } from '@/features/account/AccountScreen'
import { AddEditSheet } from '@/features/bookmarks/AddEditSheet'
import { ContextSheet, DetailSheet, DuplicateSheet } from '@/features/bookmarks/BookmarkSheets'
import { CategoriesScreen } from '@/features/categories/CategoriesScreen'
import { CategoryEditDialog, DeleteCategoryDialog } from '@/features/categories/CategoryDialogs'
import { HomeScreen } from '@/features/home/HomeScreen'
import { SearchScreen } from '@/features/search/SearchScreen'
import { ImportPreviewSheet } from '@/features/settings/ImportPreviewSheet'
import { SettingsScreen } from '@/features/settings/SettingsScreen'
import { SnackbarHost, useSnackbar } from '@/components/ui'
import { useData } from '@/data/repo'
import { useIsDesktop } from './hooks'
import { useGlobalShortcuts } from './shortcuts'
import { BottomBar, Sidebar } from './Navigation'
import { openAdd, useUi } from './uiStore'
import { useUpdatePrompt } from './pwa'

/** Web Share Target landing: /add?url=&text=&title= opens the Add sheet pre-filled. */
function ShareTarget() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const loaded = useData((s) => s.loaded)
  useEffect(() => {
    if (!loaded) return
    const text = [params.get('url'), params.get('text')].filter(Boolean).join(' ')
    const found = extract(text, params.get('title'))
    openAdd({ url: found.primaryUrl ?? found.rawText, title: found.subjectTitle ?? undefined })
    navigate('/', { replace: true })
  }, [loaded, params, navigate])
  return null
}

function Overlays() {
  const { addEdit, context, detail, duplicate, categoryDialog, deleteCategoryId, importPreview } = useUi()
  return (
    <>
      {addEdit ? <AddEditSheet key={addEdit.editId ?? 'new'} state={addEdit} /> : null}
      {context ? <ContextSheet id={context} /> : null}
      {detail ? <DetailSheet id={detail} /> : null}
      {duplicate ? <DuplicateSheet id={duplicate} /> : null}
      {categoryDialog ? <CategoryEditDialog key={categoryDialog.id ?? 'new'} id={categoryDialog.id} /> : null}
      {deleteCategoryId ? <DeleteCategoryDialog id={deleteCategoryId} /> : null}
      {importPreview ? <ImportPreviewSheet fileName={importPreview.fileName} bytes={importPreview.bytes} /> : null}
    </>
  )
}

/** Tells the user why they were signed out when the server revoked their token. */
function SessionExpiredNotice() {
  const expired = useSession((s) => s.expired)
  const show = useSnackbar((s) => s.show)
  useEffect(() => {
    if (expired) {
      show('You were signed out. Sign in again to keep syncing.')
      useSession.getState().dismissExpired()
    }
  }, [expired, show])
  return null
}

export function App() {
  const desktop = useIsDesktop()
  const { pathname } = useLocation()
  useGlobalShortcuts()
  useUpdatePrompt()

  const hideBottomBar = pathname === '/search'
  return (
    <div className="app" style={{ ['--bottom-bar' as string]: desktop || hideBottomBar ? '0px' : 'calc(72px + var(--safe-bottom))' }}>
      {desktop ? <Sidebar /> : null}
      <main className="app-main" id="main">
        <div className="content">
          <Routes>
            <Route path="/" element={<HomeScreen />} />
            <Route path="/categories" element={<CategoriesScreen />} />
            <Route path="/settings" element={<SettingsScreen />} />
            <Route path="/search" element={desktop ? <Navigate to="/" replace /> : <SearchScreen />} />
            <Route path="/account" element={<AccountScreen />} />
            <Route path="/add" element={<ShareTarget />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </main>
      {!desktop && !hideBottomBar ? <BottomBar /> : null}
      <Overlays />
      <SnackbarHost />
      <SessionExpiredNotice />
    </div>
  )
}
