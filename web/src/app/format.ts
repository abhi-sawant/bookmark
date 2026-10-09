/** "9 Oct" -- the Android detail sheet's `d MMM`. */
export const formatDayMonth = (ms: number): string =>
  new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(ms)

/** "9 October" -- the duplicate sheet's `d MMMM`. */
export const formatDayMonthLong = (ms: number): string =>
  new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'long' }).format(ms)

/** Minute-resolution relative time, like Android's DateUtils.getRelativeTimeSpanString. */
export function relativeTime(ms: number, now = Date.now()): string {
  const diff = ms - now
  const abs = Math.abs(diff)
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  if (abs < 60_000) return rtf.format(0, 'minute') // "this minute"
  if (abs < 3_600_000) return rtf.format(Math.round(diff / 60_000), 'minute')
  if (abs < 86_400_000) return rtf.format(Math.round(diff / 3_600_000), 'hour')
  return rtf.format(Math.round(diff / 86_400_000), 'day')
}

export const plural = (n: number, one: string, many = `${one}s`): string => `${n} ${n === 1 ? one : many}`
