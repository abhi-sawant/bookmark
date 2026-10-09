import { indexFor } from '@/core/util/domainColor'
import { monogram } from '@/core/util/titleFallback'
import { CATEGORY_SWATCHES } from '@/data/swatches'

/** #RRGGBB for a signed 32-bit ARGB int as Android stores accent colours. */
export function argbToHex(argb: number): string {
  return `#${(argb & 0xffffff).toString(16).padStart(6, '0')}`
}

function mix(hex: string, other: string, t: number): string {
  const a = parseInt(hex.slice(1), 16)
  const b = parseInt(other.slice(1), 16)
  const ch = (shift: number) => Math.round(((a >> shift) & 255) * (1 - t) + ((b >> shift) & 255) * t)
  return `rgb(${ch(16)}, ${ch(8)}, ${ch(0)})`
}

interface Props {
  url: string
  accentColor?: number | null
  /** Letter size in px: grid 34, list 24, detail 56, preview 26, context sheet 17. */
  fontSize?: number
}

/** The generated fallback tile: a domain-hashed gradient with the site's initials. */
export function MonogramTile({ url, accentColor, fontSize = 34 }: Props) {
  const base = accentColor != null ? argbToHex(accentColor) : CATEGORY_SWATCHES[indexFor(url)]
  return (
    <div
      className="monogram"
      aria-hidden="true"
      style={{
        fontSize,
        background: `linear-gradient(to bottom right, ${mix(base, '#ffffff', 0.1)}, ${mix(base, '#0b0d0e', 0.42)})`,
      }}
    >
      <span>{monogram(url)}</span>
    </div>
  )
}
