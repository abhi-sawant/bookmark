# Design system — Slate

The visual language of the Android app. Chosen from three candidates (kept in
[docs/design/slate-candidates.html](docs/design/slate-candidates.html), with the two
unchosen directions, Paper and Ribbon). It replaces the earlier "Aurora" look
(glass, orbs, gradients; prototype archived in `docs/design/`).

Source of truth in code: `app/src/main/java/com/bookmark/core/ui/theme/`
(`Color.kt`, `Type.kt`, `Shape.kt`, `Surfaces.kt`, `Theme.kt`).

## Principles

1. **Calm.** Flat, solid surfaces. Depth comes from a 1dp hairline, never from blur,
   glow or gradient. Nothing animates or costs anything while scrolling.
2. **One accent.** Green appears only where something is on, primary or a link:
   switches, FAB, primary buttons, the active bottom tab, pin glyphs. Selection in
   chips and segmented controls uses the ink itself, not the accent.
3. **Content first.** Thumbnails carry the grid; chrome stays small. Category is a
   colour dot beside a name, never colour alone.
4. **Quiet voice.** Lowercase screen titles and section labels, tight tracking on
   large type, Hanken Grotesk everywhere.

## Colour

| Token | Light | Dark |
|---|---|---|
| background / surface | `#F2F3F4` | `#0B0D0E` |
| card surface (`cardSurface`) | `#FFFFFF` | `#141718` |
| ink (`onSurface`) | `#0F1214` | `#ECEFF0` |
| muted (`onSurfaceVariant`, `mutedLabel`) | `#6A7076` | `#8B9298` |
| hairline (`hairline`, `outlineVariant`) | `#E3E5E8` | `#222729` |
| accent (`accent`) | `#0B7A54` | `#5EE0A9` |
| on accent | `#FFFFFF` | `#06231A` |
| accent soft (`accentSoft`) | `#E4EDE9` | `#12231C` |
| error | `#C4352F` | `#FF8A84` |

True black (OLED) keeps the dark palette on a `#000000` ground. Dynamic colour, when
on, re-tints `accent`, `accentSoft` and the M3 primary roles from the wallpaper;
neutrals stay Slate.

Category swatches and the domain-hash tile palette are unchanged, so saved
categories keep their colours.

## Typography

Hanken Grotesk (variable, SIL OFL; `res/font/hanken_grotesk.ttf`, licence in
`assets/licenses/`). Styles live in `BookmarkTextStyles`:

- Screen title: 30sp Bold, -0.04em, lowercase
- Sheet / dialog / detail title: 22–23sp Bold, -0.03em
- Card title: 14.5sp SemiBold; row title: 15sp SemiBold
- Description / subtitle: 12.5–13sp Medium
- Section label: 13sp SemiBold, lowercase, muted
- Button: 15sp Bold; chip: 14sp SemiBold

## Shape and spacing

Radii (`BookmarkShapes`): thumbnail 18, settings group 22, field 14, chip 12, FAB 19,
primary button 16, icon button 14, sheet / dialog 28. Count pills are fully round.
Screen gutter 20dp, grid gutter 12dp.

## Components

- **Header:** lowercase title, count pill, search icon button. No overflow menu.
- **Home controls:** category chips (outlined; selected = ink fill), then a row with
  the sort button (opens the sort menu) and a list/grid toggle.
- **Grid card:** no container. 18dp thumbnail, title, category dot + site. Pinned
  items show an accent flag chip on the thumbnail.
- **List row:** hairline card, 64dp thumbnail.
- **Settings:** lowercase section labels over rounded grouped cards; leading icons in
  accent-soft squares; theme as a segmented control; "Sign out" in error colour.
- **Bottom bar:** flat, docked, one hairline above; active tab marked by an
  accent-soft pill behind its icon.
- **FAB:** 58dp rounded square, solid accent.
- **Switch:** 46×28 pill, accent when on.

## App icon

Flat green bookmark ribbon on `#0B0D0E`.
