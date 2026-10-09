import { host, registrableDomain } from './urlNormalizer'

/** Number of swatches the monogram tile palette holds. */
export const PALETTE_SIZE = 8

const FNV_OFFSET_BASIS = -2128831035 // 2166136261 as a signed 32-bit int
const FNV_PRIME = 16777619

/**
 * Deterministic swatch index for a site. FNV-1a over UTF-16 code units in
 * signed 32-bit arithmetic -- identical to Android's DomainColor so a site gets
 * the same colour on both platforms.
 */
export function indexForKey(key: string): number {
  let hash = FNV_OFFSET_BASIS
  for (let i = 0; i < key.length; i++) {
    hash = Math.imul(hash ^ key.charCodeAt(i), FNV_PRIME) | 0
  }
  return ((hash % PALETTE_SIZE) + PALETTE_SIZE) % PALETTE_SIZE
}

export function indexFor(url: string): number {
  return indexForKey(registrableDomain(url) ?? host(url) ?? url)
}
