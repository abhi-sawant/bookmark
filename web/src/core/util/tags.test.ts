import { describe, expect, it } from 'vitest'
import { TAG_MAX_COUNT, TAG_MAX_LENGTH, normalizeTags, parseTags, tagQuery } from './tags'

describe('tags', () => {
  it('normalises case, hashes, spacing and duplicates', () => {
    expect(normalizeTags(['  #Read   Later ', 'KOTLIN', 'kotlin', '#', ' '])).toEqual(['read later', 'kotlin'])
  })
  it('parses commas and new lines', () => {
    expect(parseTags('a, b\nc,,')).toEqual(['a', 'b', 'c'])
  })
  it('caps length and count', () => {
    expect(normalizeTags(['x'.repeat(50)])[0]).toHaveLength(TAG_MAX_LENGTH)
    expect(normalizeTags(Array.from({ length: 25 }, (_, i) => `t${i}`))).toHaveLength(TAG_MAX_COUNT)
  })
  it('recognises #tag searches', () => {
    expect(tagQuery('  #Kotlin ')).toBe('kotlin')
    expect(tagQuery('kotlin')).toBeNull()
    expect(tagQuery('#')).toBeNull()
  })
})
