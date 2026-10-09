// Sets the theme before first paint so there is no light flash in dark mode.
// External (not inline) so the Content-Security-Policy can stay `script-src 'self'`.
try {
  var p = JSON.parse(localStorage.getItem('bookmark.prefs') || '{}')
  var dark = p.theme === 'DARK' || ((!p.theme || p.theme === 'SYSTEM') && matchMedia('(prefers-color-scheme: dark)').matches)
  var r = document.documentElement
  r.dataset.theme = dark ? 'dark' : 'light'
  r.dataset.black = String(dark && !!p.trueBlack)
  var bg = !dark ? '#f2f3f4' : p.trueBlack ? '#000000' : '#0b0d0e'
  document.querySelector('meta[name=theme-color]').setAttribute('content', bg)
} catch (e) {}
