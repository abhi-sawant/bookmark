/** The desktop shortcuts, as shown in the cheat-sheet (`?`). Keep in step with shortcuts.ts. */
export interface ShortcutEntry {
  keys: string[]
  /** How the keys combine: pressed together, or one after another. */
  join?: 'then'
  action: string
}

export const SHORTCUTS: ShortcutEntry[] = [
  { keys: ['N'], action: 'Add a bookmark' },
  { keys: ['/'], action: 'Search' },
  { keys: ['G', 'H'], join: 'then', action: 'Go to Home' },
  { keys: ['G', 'C'], join: 'then', action: 'Go to Categories' },
  { keys: ['G', 'S'], join: 'then', action: 'Go to Settings' },
  { keys: ['Esc'], action: 'Close a sheet, clear search, or stop selecting' },
  { keys: ['?'], action: 'Show this list' },
]
