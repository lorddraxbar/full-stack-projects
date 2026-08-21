// Shared status/priority label + color maps, aligned to the seeded
// dropdown_values in the database (V2 migration).

export const PROJECT_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: 'Not Started',
  IN_PROGRESS: 'In Progress',
  ON_HOLD: 'On Hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
}

export const TASK_STATUS_LABELS: Record<string, string> = {
  TO_DO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
}

export const PRIORITY_LABELS: Record<string, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
}

export const PROJECT_STATUS_COLORS: Record<string, string> = {
  'Not Started': 'bg-gray-100 text-gray-800',
  'In Progress': 'bg-teal-100 text-teal-800',
  'On Hold': 'bg-red-100 text-red-800',
  'Completed': 'bg-green-100 text-green-800',
  'Archived': 'bg-gray-100 text-gray-500',
}

export const PRIORITY_COLORS: Record<string, string> = {
  'High': 'bg-red-100 text-red-800',
  'Medium': 'bg-yellow-100 text-yellow-800',
  'Low': 'bg-green-100 text-green-800',
}

export const TASK_STATUS_COLORS: Record<string, string> = {
  'To Do': 'bg-gray-100 text-gray-800',
  'In Progress': 'bg-teal-100 text-teal-800',
  'Done': 'bg-green-100 text-green-800',
}

export const DOCUMENT_CATEGORY_LABELS: Record<string, string> = {
  CLIENT_SUBMITTED: 'Client-Submitted',
  REQUESTED: 'Requested',
  DELIVERABLE: 'Deliverable',
}

export const DOCUMENT_CATEGORY_COLORS: Record<string, string> = {
  'Client-Submitted': 'bg-green-100 text-green-800',
  'Requested': 'bg-yellow-100 text-yellow-800',
  'Deliverable': 'bg-teal-100 text-teal-800',
}

export const ANNOUNCEMENT_CATEGORY_LABELS: Record<string, string> = {
  PROJECT_UPDATE: 'Project Update',
  COMPANY_NEWS: 'Company News',
  MAINTENANCE: 'Maintenance',
}

export const ANNOUNCEMENT_CATEGORY_COLORS: Record<string, string> = {
  'Project Update': 'bg-teal-100 text-teal-800',
  'Company News': 'bg-purple-100 text-purple-800',
  'Maintenance': 'bg-orange-100 text-orange-800',
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

export function documentCategoryLabel(code: string | null | undefined): string {
  if (!code) return 'Uncategorized'
  return DOCUMENT_CATEGORY_LABELS[code] || code
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

export function projectStatusLabel(code: string | null | undefined): string {
  if (!code) return 'Unknown'
  return PROJECT_STATUS_LABELS[code] || code
}

export function taskStatusLabel(code: string | null | undefined): string {
  if (!code) return 'Unknown'
  return TASK_STATUS_LABELS[code] || code
}

export function priorityLabel(code: string | null | undefined): string {
  if (!code) return 'Medium'
  return PRIORITY_LABELS[code] || code
}

export function formatDate(d: string | null | undefined): string {
  if (!d) return '—'
  // Accepts ISO date (2026-09-30) or datetime (2026-09-30T10:00:00)
  const date = new Date(d)
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
