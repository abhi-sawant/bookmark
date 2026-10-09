import type { ThumbnailChoice } from '@/data/models'

const MAX_EDGE = 1080
const QUALITY = 0.8
export const MAX_THUMBNAIL_BYTES = 3 * 1024 * 1024

export class ThumbnailError extends Error {}

/**
 * Turns a user-picked image into what the backend accepts: lossy WebP, longest edge <= 1080px,
 * <= 3 MB (same recipe as Android's ThumbnailPipeline). Browsers that cannot encode WebP
 * (Safari's canvas) fall back to PNG, which the server rejects, so we report that instead.
 */
export async function fileToThumbnail(file: File): Promise<ThumbnailChoice> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new ThumbnailError("That file isn't an image we can read.")
  }
  if (bitmap.width < 64 || bitmap.height < 64) throw new ThumbnailError('That image is too small to use as a thumbnail.')
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
  const width = Math.max(1, Math.round(bitmap.width * scale))
  const height = Math.max(1, Math.round(bitmap.height * scale))

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new ThumbnailError("Couldn't process that image.")
  ctx.drawImage(bitmap, 0, 0, width, height)

  // Average colour from a 1x1 downscale: the placeholder shown while the image loads.
  const px = document.createElement('canvas')
  px.width = px.height = 1
  const pctx = px.getContext('2d')!
  pctx.drawImage(canvas, 0, 0, 1, 1)
  const [r, g, b] = pctx.getImageData(0, 0, 1, 1).data
  const accentColor = (0xff000000 | (r << 16) | (g << 8) | b) | 0
  bitmap.close()

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', QUALITY))
  if (!blob || blob.type !== 'image/webp') throw new ThumbnailError("This browser can't create WebP thumbnails.")
  if (blob.size > MAX_THUMBNAIL_BYTES) throw new ThumbnailError('That image is too large even after shrinking.')
  return { blob, width, height, accentColor }
}
