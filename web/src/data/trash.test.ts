import { describe, expect, it } from 'vitest'
import { trashCutoff, trashDaysLeft } from './trash'

const day = 24 * 60 * 60 * 1000

describe('trash policy', () => {
  it('cutoff is thirty days back', () => {
    expect(trashCutoff(100 * day)).toBe(70 * day)
  })
  it('days left rounds up and never goes negative', () => {
    const now = 100 * day
    expect(trashDaysLeft(now, now)).toBe(30)
    expect(trashDaysLeft(now - 1, now)).toBe(30)
    expect(trashDaysLeft(now - 29 * day - 1, now)).toBe(1)
    expect(trashDaysLeft(now - 30 * day, now)).toBe(0)
    expect(trashDaysLeft(now - 90 * day, now)).toBe(0)
  })
})
