import { describe, expect, it } from 'vitest'
import { SHORTCUTS } from './shortcutList'

describe('shortcut list', () => {
  it('has an entry for every handled key and no duplicates', () => {
    const labels = SHORTCUTS.map((s) => s.keys.join('+'))
    for (const k of ['N', '/', 'G+H', 'G+C', 'G+S', '?']) expect(labels).toContain(k)
    expect(new Set(labels).size).toBe(labels.length)
  })
})
