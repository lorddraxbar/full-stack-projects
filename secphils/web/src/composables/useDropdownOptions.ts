import { computed, type ComputedRef } from 'vue'
import { useGetDropdowns, type DropdownValueItem } from '@/services/api'
import {
  PROJECT_STATUS_LABELS, PROJECT_STATUS_COLORS,
  ANNOUNCEMENT_CATEGORY_LABELS, ANNOUNCEMENT_CATEGORY_COLORS,
  ANNOUNCEMENT_AUDIENCE_LABELS,
} from '@/lib/labels'

/**
 * Dropdown options consumed live from Admin → Project Config (V35 wiring).
 *
 * Contract: a form reads its category's values from the DB; the built-in
 * maps in lib/labels.ts are the FALLBACK (offline, fetch failure, or a fresh
 * DB whose categories were edited away). Label precedence is the DB row's
 * displayLabel; colors are palette-derived because they key on the CODE —
 * a new admin-defined code always lands on a sensible color, and renaming a
 * code's label never breaks badge styling.
 */

interface OptionsDef {
  values: DropdownValueItem[]
  /** code → display label, DB row wins over the built-in map */
  label: (code: string | null | undefined) => string
  /** code → tailwind badge classes, palette-derived for unknown codes */
  color: (code: string | null | undefined) => string
}

const fallback = (
  labels: Record<string, string>,
  colors: Record<string, string>,
  defaultColor = 'bg-gray-100 text-gray-800',
): OptionsDef => ({
  values: Object.entries(labels).map(([value, displayLabel], i) => ({
    id: -(i + 1), value, displayLabel, sortOrder: i,
  })),
  label: (c) => (c ? labels[c] || c.replace(/_/g, ' ') : '—'),
  // PROJECT_STATUS_COLORS is keyed by LABEL ("Not Started"), announcement maps
  // by CODE — try both.
  color: (c) => (c ? colors[labels[c] || ''] || colors[c] || defaultColor : defaultColor),
})

const PALETTE = [
  'bg-teal-100 text-teal-800',
  'bg-blue-100 text-blue-800',
  'bg-purple-100 text-purple-800',
  'bg-orange-100 text-orange-800',
  'bg-green-100 text-green-800',
  'bg-indigo-100 text-indigo-800',
]

function hashColor(code: string): string {
  let h = 0
  for (let i = 0; i < code.length; i++) h = (h * 31 + code.charCodeAt(i)) | 0
  return PALETTE[Math.abs(h) % PALETTE.length]
}

// module-level cache: one fetch per category for the whole SPA session.
const cache = new Map<string, OptionsDef>()
const inflight: Record<string, Promise<void>> = {}

function buildFallback(category: string): OptionsDef {
  switch (category) {
    case 'project_status': return fallback(PROJECT_STATUS_LABELS, PROJECT_STATUS_COLORS)
    case 'announcement_category': return fallback(ANNOUNCEMENT_CATEGORY_LABELS, ANNOUNCEMENT_CATEGORY_COLORS)
    case 'audience': return fallback(ANNOUNCEMENT_AUDIENCE_LABELS, {
      COMPANY: 'bg-purple-100 text-purple-800',
      PROJECT: 'bg-teal-100 text-teal-800',
    })
    default: return fallback({}, {})
  }
}

async function fetchCategory(category: string): Promise<void> {
  try {
    const cats = await useGetDropdowns()
    const hit = cats.find(c => c.name === category)
    if (hit && hit.values && hit.values.length > 0) {
      const builtin = buildFallback(category)
      const builtinLabels = Object.fromEntries(builtin.values.map(v => [v.value, v.displayLabel]))
      const byValue = Object.fromEntries(hit.values.map(v => [v.value, v]))
      cache.set(category, {
        values: hit.values,
        label: (c) => {
          if (!c) return '—'
          const row = byValue[c]
          if (row) return row.displayLabel || row.value
          return builtinLabels[c] || c.replace(/_/g, ' ')
        },
        // Built-in colors win for the codes the shipped palette knows (badge
        // identity stays pixel-identical to the pre-wiring UI); a new
        // admin-defined code gets a stable palette hash.
        color: (c) => {
          if (!c) return 'bg-gray-100 text-gray-800'
          if (builtinLabels[c]) return builtin.color(c)
          return hashColor(c)
        },
      })
    } else {
      cache.set(category, buildFallback(category))
    }
  } catch {
    cache.set(category, buildFallback(category))
  }
}

export function useDropdownOptions(category: string): {
  options: ComputedRef<{ value: string; label: string }[]>
  label: (code: string | null | undefined) => string
  color: (code: string | null | undefined) => string
  ready: ComputedRef<boolean>
} {
  if (!inflight[category]) {
    inflight[category] = fetchCategory(category)
  }
  const current = () => cache.get(category) || buildFallback(category)

  return {
    options: computed(() =>
      current().values.map(v => ({ value: v.value, label: v.displayLabel || v.value }))),
    label: (code) => current().label(code),
    color: (code) => current().color(code),
    ready: computed(() => cache.has(category)),
  }
}

/** Invalidate one category's cache — call after Admin edits its values so
 *  every open surface refetches on next mount. */
export function invalidateDropdownOptions(category?: string) {
  if (category) {
    cache.delete(category)
    delete inflight[category]
  }
}
