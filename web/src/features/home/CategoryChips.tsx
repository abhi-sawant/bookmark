import { useData } from '@/data/repo'
import { CategoryChip } from '@/components/ui'
import { parseCategoryColor } from '@/data/swatches'

export function CategoryChips({ selected, onSelect, showCounts = true }: { selected: string | null; onSelect: (id: string | null) => void; showCounts?: boolean }) {
  const categories = useData((s) => s.categories)
  const total = useData((s) => s.bookmarks.length)
  return (
    <div className="chip-row" role="group" aria-label="Filter by category">
      <CategoryChip label="All" selected={selected === null} count={showCounts ? total : undefined} onClick={() => onSelect(null)} />
      {categories.map((c) => (
        <CategoryChip key={c.id} label={c.name} color={parseCategoryColor(c.colorHex)} selected={selected === c.id} onClick={() => onSelect(selected === c.id ? null : c.id)} />
      ))}
    </div>
  )
}
