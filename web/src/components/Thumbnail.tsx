import { useEffect, useState } from 'react'
import type { Bookmark } from '@/data/models'
import { MonogramTile, argbToHex } from './MonogramTile'

export function thumbSrc(b: Bookmark): string | null {
  return b.thumbnailUrl ? `${b.thumbnailUrl}?v=${b.updatedAt}` : null
}

interface Props {
  bookmark: Bookmark
  size?: number
  radius?: number
  fontSize?: number
  /** Fixed-size square (list rows) vs. filling a parent with its own aspect ratio. */
  className?: string
  style?: React.CSSProperties
}

/** The remote thumbnail if there is one (and it loads), else the monogram tile. */
export function Thumbnail({ bookmark, size, radius, fontSize, className = '', style }: Props) {
  // A thumbnail picked on this device that has not been uploaded yet.
  const pending = bookmark.pendingThumbnail
  const [localUrl, setLocalUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!pending) return setLocalUrl(null)
    const u = URL.createObjectURL(pending)
    setLocalUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [pending])
  const src = localUrl ?? thumbSrc(bookmark)
  const [failed, setFailed] = useState(false)
  const showImage = src !== null && !failed
  return (
    <div
      className={`thumb ${className}`}
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        background: bookmark.accentColor != null ? argbToHex(bookmark.accentColor) : undefined,
        ...style,
      }}
    >
      {showImage ? (
        <img src={src} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />
      ) : (
        <MonogramTile url={bookmark.url} accentColor={bookmark.accentColor} fontSize={fontSize} />
      )}
    </div>
  )
}
