export interface Subtask {
  id: number
  title: string
  completed: boolean
}

export interface Task {
  id: number
  title: string
  description: string
  status: 'todo' | 'in-progress' | 'review' | 'done'
  priority: 'low' | 'medium' | 'high' | 'urgent'
  assignee: string
  assigneeId: number | null
  dueDate: string
  projectId: number
  projectTitle: string
  subtasks: Subtask[]
}

export const STATUS_LABELS: Record<Task['status'], string> = {
  'todo': 'To Do',
  'in-progress': 'In Progress',
  'review': 'Review',
  'done': 'Done',
}

export const PRIORITY_LABELS: Record<Task['priority'], string> = {
  'low': 'Low',
  'medium': 'Medium',
  'high': 'High',
  'urgent': 'Urgent',
}
