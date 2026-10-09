/** The eight category swatches, in order. Mirrors android/.../ui/theme/Color.kt CategorySwatches. */
export const CATEGORY_SWATCHES = [
  '#FB923C',
  '#38BDF8',
  '#A78BFA',
  '#6C8CFF',
  '#F472B6',
  '#8F96C4',
  '#FACC15',
  '#34D399',
] as const

/** Swatches an older app version used, index for index. Imported backups may carry them. */
const LEGACY_SWATCHES = ['#B0552F', '#0F7A6B', '#7A4FD6', '#2A5FD6', '#C0392B', '#7D918D', '#7D5416', '#00504A']

export function normalizeCategoryHex(hex: string): string {
  const i = LEGACY_SWATCHES.indexOf(hex.toUpperCase())
  return i >= 0 ? CATEGORY_SWATCHES[i] : hex
}

/** Accepts #RRGGBB or #AARRGGBB; anything else falls back to the neutral swatch. */
export function parseCategoryColor(hex: string): string {
  const m = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.exec(normalizeCategoryHex(hex))
  if (!m) return CATEGORY_SWATCHES[5]
  return `#${m[1].slice(-6)}`
}

/** The five icon choices in the category dialog; null means no icon. */
export const CATEGORY_ICON_KEYS = ['play', 'edit', 'star', 'home'] as const
