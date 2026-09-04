import { useGetLanding } from '@/services/api'

/**
 * Runtime brand theming for the whole app shell.
 *
 * The app's "color scheme" is admin-configurable (Admin → Company Settings →
 * Color Scheme): brandPrimary/brandSecondary already drive the public landing
 * and legal pages via their own --bsp vars. This composable makes the ENTIRE
 * shell follow the same scheme by overriding Tailwind v4's :root color tokens
 * (utilities resolve var(--color-*) at runtime — verified against the built
 * CSS) and the matching shadcn --* symbols, with values derived from the brand
 * primary hue.
 *
 * Contract: native Emerald (#29ca8e) applies NO override — the shipped look
 * stays pixel-identical, so nothing changes until an admin actually picks a
 * different scheme (Indigo/Ocean/Teal/Violet/Slate/custom). Semantic colors
 * (status greens/ambers/reds, gray/slate badges) are deliberately untouched —
 * only the brand-accent hue family is re-themed.
 */

export const BRAND_THEME_DEFAULT_PRIMARY = '#29ca8e'
export const BRAND_THEME_DEFAULT_SECONDARY = '#536976'
const CACHE_KEY = 'secphils:brand-theme'

const OVERRIDE_KEYS = [
  '--color-primary', '--primary',
  '--color-primary-foreground', '--primary-foreground',
  '--color-ring', '--ring',
  '--color-accent', '--accent',
  '--color-accent-foreground', '--accent-foreground',
  '--color-chart-1', '--chart-1',
  '--color-chart-3', '--chart-3',
  '--color-sidebar-primary', '--sidebar-primary',
  '--color-sidebar-primary-foreground', '--sidebar-primary-foreground',
  '--color-sidebar-accent', '--sidebar-accent',
  '--color-sidebar-accent-foreground', '--sidebar-accent-foreground',
  '--color-sidebar-ring', '--sidebar-ring',
  '--color-emerald-50', '--color-emerald-100', '--color-emerald-200',
  '--color-emerald-300', '--color-emerald-400', '--color-emerald-500',
  '--color-emerald-600', '--color-emerald-700', '--color-emerald-800',
  '--color-emerald-900',
]

const isHex = (v: unknown): v is string =>
  typeof v === 'string' && /^#[0-9a-f]{6}$/i.test(v.trim())

const hexToRgb = (hex: string): [number, number, number] => {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

/** pct < 0 → mix toward black; pct > 0 → mix toward white. */
const shade = (hex: string, pct: number): string => {
  const [r, g, b] = hexToRgb(hex)
  const t = pct < 0 ? 0 : 255
  const m = Math.abs(pct)
  const f = (x: number) => Math.round(x + (t - x) * m)
  const hx = (x: number) => x.toString(16).padStart(2, '0')
  return `#${hx(f(r))}${hx(f(g))}${hx(f(b))}`
}

export function applyBrandTheme(primary?: string | null, secondary?: string | null): void {
  const root = document.documentElement
  const clear = () => OVERRIDE_KEYS.forEach((k) => root.style.removeProperty(k))
  const p = isHex(primary) ? primary.trim() : null
  const s = isHex(secondary) ? secondary.trim() : BRAND_THEME_DEFAULT_SECONDARY

  clear()
  if (!p || p.toLowerCase() === BRAND_THEME_DEFAULT_PRIMARY) {
    // Native Emerald — the shipped look wins; drop any cached scheme.
    try { localStorage.removeItem(CACHE_KEY) } catch { /* ignore */ }
    return
  }

  const set = (k: string, v: string) => root.style.setProperty(k, v)
  const light = (l: number) => shade(p, l)

  set('--color-primary', p); set('--primary', p)
  set('--color-primary-foreground', '#ffffff'); set('--primary-foreground', '#ffffff')
  set('--color-ring', p); set('--ring', p)
  set('--color-accent', light(0.93)); set('--accent', light(0.93))
  set('--color-accent-foreground', light(-0.25)); set('--accent-foreground', light(-0.25))
  set('--color-chart-1', p); set('--chart-1', p)
  set('--color-chart-3', s); set('--chart-3', s)
  set('--color-sidebar-primary', p); set('--sidebar-primary', p)
  set('--color-sidebar-primary-foreground', '#ffffff'); set('--sidebar-primary-foreground', '#ffffff')
  set('--color-sidebar-accent', light(0.93)); set('--sidebar-accent', light(0.93))
  set('--color-sidebar-accent-foreground', light(-0.25)); set('--sidebar-accent-foreground', light(-0.25))
  set('--color-sidebar-ring', p); set('--sidebar-ring', p)

  // Emerald scale — the app's accent workhorse (buttons, active nav, badges,
  // focus rings, toggles). Anchor 500 = the brand itself, then lighten/darken
  // along the brand's own hue so the old emerald-* call sites keep their
  // hierarchy (50 lightest … 900 darkest).
  set('--color-emerald-50', light(0.94))
  set('--color-emerald-100', light(0.87))
  set('--color-emerald-200', light(0.75))
  set('--color-emerald-300', light(0.55))
  set('--color-emerald-400', light(0.32))
  set('--color-emerald-500', p)
  set('--color-emerald-600', light(-0.14))
  set('--color-emerald-700', light(-0.30))
  set('--color-emerald-800', light(-0.44))
  set('--color-emerald-900', light(-0.55))

  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ primary: p, secondary: s }))
  } catch { /* ignore */ }
}

/** Synchronous bootstrap restore (pre-paint, avoids a flash on reload). */
export function applyCachedBrandTheme(): void {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return
    const { primary, secondary } = JSON.parse(raw)
    applyBrandTheme(primary, secondary)
  } catch { /* corrupt/missing cache — leave the default */ }
}

/** Live refresh from the public /landing payload (works pre-login). */
export async function refreshBrandTheme(): Promise<void> {
  try {
    const data = await useGetLanding()
    applyBrandTheme(data.company?.brandPrimary, data.company?.brandSecondary)
  } catch {
    /* landing unreachable — keep whatever is already applied */
  }
}