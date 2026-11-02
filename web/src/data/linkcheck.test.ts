import { describe, expect, it } from 'vitest'
import type { Bookmark } from './models'
import { chunk, summarizeLinkChecks } from './linkcheck'

describe('link check helpers', () => {
  it('splits into batches of the given size', () => {
    expect(chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]])
    expect(chunk([], 10)).toEqual([])
  })
  it('summarises only checked bookmarks', () => {
    const mk = (linkStatus?: Bookmark['linkStatus']) => ({ linkStatus }) as Bookmark
    expect(summarizeLinkChecks([mk('ok'), mk('broken'), mk('error'), mk(), mk('broken')])).toEqual({ checked: 4, broken: 2, unreachable: 1 })
  })
})
