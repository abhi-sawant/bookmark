import { describe, expect, it } from 'vitest'
import type { Bookmark } from '@/data/models'
import { pickSurprise } from './surprise'

const mk = (id: string, createdAt: number) => ({ id, createdAt }) as Bookmark

describe('pickSurprise', () => {
  it('returns null for an empty list and the only item for one', () => {
    expect(pickSurprise([])).toBeNull()
    expect(pickSurprise([mk('a', 1)])?.id).toBe('a')
  })
  it('only ever picks from the older half', () => {
    const list = [mk('d', 4), mk('a', 1), mk('c', 3), mk('b', 2)]
    for (const r of [0, 0.25, 0.5, 0.75, 0.999999]) expect(['a', 'b']).toContain(pickSurprise(list, () => r)?.id)
    expect(pickSurprise(list, () => 0)?.id).toBe('a')
    expect(pickSurprise(list, () => 0.999999)?.id).toBe('b')
  })
})
