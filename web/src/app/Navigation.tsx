import { useMemo } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '@/account/session'
import { parseCategoryColor } from '@/data/swatches'
import { bookmarkCountByCategory, useData } from '@/data/repo'
import { Icon, type IconName } from '@/components/Icon'
import { CategoryDot, PrimaryButton } from '@/components/ui'
import { useSyncStatus } from '@/sync/engine'
import { openAdd, useUi } from './uiStore'
import { relativeTime } from './format'

const TABS: { to: string; label: string; icon: IconName }[] = [
  { to: '/', label: 'Home', icon: 'home' },
  { to: '/categories', label: 'Categories', icon: 'viewList' },
  { to: '/settings', label: 'Settings', icon: 'settings' },
]

export function BottomBar() {
  return (
    <nav className="bottom-bar" aria-label="Main">
      {TABS.map((t) => (
        <NavLink key={t.to} to={t.to} end className={({ isActive }) => `tab ${isActive ? 'active' : ''}`}>
          <span className="pill">
            <Icon name={t.icon} size={24} />
          </span>
          <span className="label">{t.label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

/** Desktop navigation: the bottom bar's tabs plus the category filter, and a sync footer. */
export function Sidebar() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const categories = useData((s) => s.categories)
  const bookmarks = useData((s) => s.bookmarks)
  const total = bookmarks.length
  const counts = useMemo(() => bookmarkCountByCategory(bookmarks, categories), [bookmarks, categories])
  const filter = useUi((s) => s.filterCategory)
  const setUi = useUi((s) => s.set)
  const email = useSession((s) => s.email)
  const { syncing, lastSyncedAt, error } = useSyncStatus()

  const pick = (id: string | null) => {
    setUi('filterCategory', id)
    if (pathname !== '/') navigate('/')
  }

  return (
    <aside className="sidebar">
      <div className="brand">
        <img src="/favicon.svg" alt="" width={32} height={32} />
        <span className="t-sheet-title">bookmarks</span>
      </div>
      <PrimaryButton block small onClick={() => openAdd()} style={{ marginBottom: 14 }}>
        <Icon name="add" size={20} /> New bookmark <kbd className="kbd-on-accent">N</kbd>
      </PrimaryButton>
      <nav className="side-nav" aria-label="Main">
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to} end className={({ isActive }) => `side-item ${isActive ? 'active' : ''}`}>
            <Icon name={t.icon} size={22} />
            <span>{t.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="section-header t-section" style={{ padding: '18px 12px 6px' }}>categories</div>
      <div className="side-cats" role="group" aria-label="Filter by category">
        <button type="button" className={`side-item ${pathname === '/' && filter === null ? 'active' : ''}`} onClick={() => pick(null)}>
          <span className="dot" style={{ background: 'var(--ink)', opacity: 0.4 }} />
          <span style={{ flex: 1, textAlign: 'left' }}>All</span>
          <span className="t-row-sub muted">{total}</span>
        </button>
        {categories.map((c) => (
          <button key={c.id} type="button" className={`side-item ${pathname === '/' && filter === c.id ? 'active' : ''}`} onClick={() => pick(c.id)}>
            <CategoryDot color={parseCategoryColor(c.colorHex)} />
            <span className="clamp-1" style={{ flex: 1, textAlign: 'left' }}>{c.name}</span>
            <span className="t-row-sub muted">{counts.get(c.id) ?? 0}</span>
          </button>
        ))}
      </div>
      <div className="side-footer t-row-sub muted">
        {email ? (
          <>
            <div className="clamp-1" style={{ color: 'var(--ink)', fontWeight: 600 }}>{email}</div>
            <div>{error ?? (syncing ? 'Syncing…' : lastSyncedAt ? `Synced ${relativeTime(lastSyncedAt)}` : 'Not synced yet')}</div>
          </>
        ) : (
          <button type="button" className="side-item" onClick={() => navigate('/account')}>
            <Icon name="sync" size={20} /> <span>Sign in to sync</span>
          </button>
        )}
      </div>
    </aside>
  )
}
