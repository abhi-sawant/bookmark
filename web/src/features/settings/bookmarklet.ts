/**
 * Builds the `javascript:` link users drag to their bookmarks bar. It opens
 * the app's `/add` route (the same one the PWA share target uses) with the
 * current page's address and title filled in.
 */
export function buildBookmarklet(origin: string): string {
  const base = `${origin.replace(/\/+$/, '')}/add`
  const body =
    `window.open(${JSON.stringify(base)}+'?url='+encodeURIComponent(location.href)` +
    `+'&title='+encodeURIComponent(document.title),'_blank')`
  return `javascript:(function(){${body}})()`
}
