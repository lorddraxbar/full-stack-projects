// Shared status/priority label + color maps, aligned to the seeded
// dropdown_values in the database (V2 migration).

export const PROJECT_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: 'Not Started',
  IN_PROGRESS: 'In Progress',
  ON_HOLD: 'On Hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
}

export const PROJECT_STATUS_COLORS: Record<string, string> = {
  'Not Started': 'bg-gray-100 text-gray-800',
  'In Progress': 'bg-teal-100 text-teal-800',
  'On Hold': 'bg-red-100 text-red-800',
  'Completed': 'bg-green-100 text-green-800',
  'Archived': 'bg-gray-100 text-gray-500',
}

export const FILE_TYPE_LABELS: Record<string, string> = {
  IMAGE: 'Image',
  PDF: 'PDF',
  WORD: 'Word',
  SPREADSHEET: 'Spreadsheet',
  PRESENTATION: 'Presentation',
  ARCHIVE: 'Archive',
  OTHER: 'Other',
}

export const FILE_TYPE_COLORS: Record<string, string> = {
  Image: 'bg-teal-100 text-teal-800',
  PDF: 'bg-red-100 text-red-800',
  Word: 'bg-blue-100 text-blue-800',
  Spreadsheet: 'bg-green-100 text-green-800',
  Presentation: 'bg-orange-100 text-orange-800',
  Archive: 'bg-purple-100 text-purple-800',
  Other: 'bg-gray-100 text-gray-700',
}

export function fileTypeLabel(code: string | null | undefined): string {
  if (!code) return 'Other'
  return FILE_TYPE_LABELS[code] || code
}

export const ANNOUNCEMENT_CATEGORY_LABELS: Record<string, string> = {
  PROJECT_UPDATE: 'Project Update',
  COMPANY_NEWS: 'Company News',
  MAINTENANCE: 'Maintenance',
}

export const ANNOUNCEMENT_CATEGORY_COLORS: Record<string, string> = {
  PROJECT_UPDATE: 'bg-teal-100 text-teal-800',
  COMPANY_NEWS: 'bg-purple-100 text-purple-800',
  MAINTENANCE: 'bg-orange-100 text-orange-800',
}

export const ANNOUNCEMENT_AUDIENCE_LABELS: Record<string, string> = {
  PROJECT: 'Project',
  COMPANY: 'Company-wide',
}

export const AUDIENCE_LABELS: Record<string, string> = {
  PROJECT: 'Project',
  COMPANY: 'Company',
}

export const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

export const REVIEW_STATUS_COLORS: Record<string, string> = {
  'Pending': 'bg-yellow-100 text-yellow-800',
  'Approved': 'bg-green-100 text-green-800',
  'Rejected': 'bg-red-100 text-red-800',
}

export function announcementCategoryLabel(code: string | null | undefined): string {
  if (!code) return 'General'
  return ANNOUNCEMENT_CATEGORY_LABELS[code] || code
}

export function audienceLabel(code: string | null | undefined): string {
  if (!code) return 'Company'
  return AUDIENCE_LABELS[code] || code
}

export function reviewStatusLabel(code: string | null | undefined): string {
  if (!code) return 'Pending'
  return REVIEW_STATUS_LABELS[code] || code
}

export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Format a Philippine Peso (PHP) amount for display, e.g. 2500000 → "₱2,500,000".
 * en-PH locale keeps digit grouping consistent with PH convention; whole-peso
 * amounts only (decimals are rounded), which matches project-level contract
 * values in this portal.
 */
export function formatPhp(value: number | null | undefined): string {
  if (value == null || isNaN(value)) return '—'
  return new Intl.NumberFormat('en-PH', {
    style: 'currency', currency: 'PHP',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  }).format(value)
}

/** Compact peso for stat cards, e.g. 2500000 → "₱2.5M", 500000 → "₱500.0K". */
export function formatPhpCompact(value: number | null | undefined): string {
  if (value == null || isNaN(value)) return '—'
  return new Intl.NumberFormat('en-PH', {
    style: 'currency', currency: 'PHP',
    notation: 'compact', maximumFractionDigits: 1,
  }).format(value)
}

export function projectStatusLabel(code: string | null | undefined): string {
  if (!code) return 'Unknown'
  return PROJECT_STATUS_LABELS[code] || code
}

export function formatDate(d: string | null | undefined): string {
  if (!d) return '—'
  // Date-only values (e.g. "2026-09-30") are parsed as UTC by `new Date()`,
  // which shifts them a day earlier in negative-UTC timezones. Parse the
  // parts as local time instead.
  let date: Date
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d)
  if (m) {
    date = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  } else {
    date = new Date(d)
  }
  if (isNaN(date.getTime())) return d
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

export function formatDateTime(d: string | null | undefined): string {
  if (!d) return '—'
  const date = new Date(d)
  if (isNaN(date.getTime())) return d
  return date.toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: 'numeric', minute: '2-digit', hour12: true,
  })
}

export function timeAgo(d: string | null | undefined): string {
  if (!d) return ''
  const then = new Date(d).getTime()
  if (Number.isNaN(then)) return ''
  const seconds = Math.floor((Date.now() - then) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return formatDate(d)
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?'
  return name.split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase()
}
