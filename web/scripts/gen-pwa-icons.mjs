// Renders the PWA icons from the launcher icon geometry (android/.../ic_launcher_foreground.xml):
// a flat green ribbon on #0B0D0E. Run once; the PNGs are committed.
import sharp from 'sharp'
import { writeFileSync } from 'node:fs'

const RIBBON = 'M42,29H66Q71,29 71,34V80L54,65L37,80V34Q37,29 42,29Z'
const svg = (rounded) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108"${rounded ? ' rx="24"' : ''} fill="#0B0D0E"/><path d="${RIBBON}" fill="#5EE0A9"/></svg>`

const out = new URL('../public/', import.meta.url)
writeFileSync(new URL('favicon.svg', out), svg(true))
const render = (s, size, file) => sharp(Buffer.from(s)).resize(size, size).png().toFile(new URL(file, out).pathname)
await render(svg(true), 192, 'icon-192.png')
await render(svg(true), 512, 'icon-512.png')
await render(svg(false), 512, 'icon-maskable-512.png')
await render(svg(false), 180, 'apple-touch-icon.png')
console.log('icons written')
