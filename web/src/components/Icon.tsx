import { ICON_PATHS, type IconName } from './iconPaths'

export type { IconName }

export function Icon({ name, size = 24, className }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      style={{ flex: 'none' }}
    >
      <path d={ICON_PATHS[name]} />
    </svg>
  )
}
