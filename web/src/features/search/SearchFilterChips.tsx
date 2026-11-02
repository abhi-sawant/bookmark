import { useUi } from '@/app/uiStore'
import { type SearchFilters } from '../home/selectors'

const CHIPS: { key: keyof SearchFilters; label: string }[] = [
  { key: 'unread', label: 'Unread' },
  { key: 'broken', label: 'Broken links' },
  { key: 'pinned', label: 'Pinned' },
  { key: 'unsorted', label: 'No category' },
  { key: 'thisWeek', label: 'This week' },
]

/** Pinned / No category / This week toggles under the category chips. */
export function SearchFilterChips() {
  const filters = useUi((s) => s.searchFilters)
  const set = useUi((s) => s.set)
  return (
    <div className="chip-row" role="group" aria-label="Quick filters">
      {CHIPS.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          className={`chip ${filters[key] ? 'active' : ''}`}
          aria-pressed={filters[key]}
          onClick={() => set('searchFilters', { ...filters, [key]: !filters[key] })}
        >
          {label}
        </button>
      ))}
    </div>
  )
}
