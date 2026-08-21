<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import TaskDetailModal from '@/components/TaskDetailModal.vue'
import { useProjectsStore } from '@/stores/projects'
import { useGetTasks, useCreateTask, useUpdateTask, useDeleteTask, useGetUsers } from '@/services/api'
import type { Task } from '@/types/task'
import { STATUS_LABELS, PRIORITY_LABELS } from '@/types/task'
import { formatDate } from '@/lib/labels'

const projectsStore = useProjectsStore()

// ---- DB code <-> frontend key mapping ----
const STATUS_TO_KEY: Record<string, Task['status']> = {
  TO_DO: 'todo', IN_PROGRESS: 'in-progress', IN_REVIEW: 'review', COMPLETED: 'done',
}
const KEY_TO_STATUS: Record<string, string> = {
  todo: 'TO_DO', 'in-progress': 'IN_PROGRESS', review: 'IN_REVIEW', done: 'COMPLETED',
}
const PRIORITY_TO_KEY: Record<string, Task['priority']> = {
  HIGH: 'high', MEDIUM: 'medium', LOW: 'low', URGENT: 'urgent',
}
const KEY_TO_PRIORITY: Record<string, string> = {
  low: 'LOW', medium: 'MEDIUM', high: 'HIGH', urgent: 'URGENT',
}

const projectName = (id: number) =>
  projectsStore.projects.find(p => p.id === id)?.name ?? `Project #${id}`

const mapTask = (t: any): Task => ({
  id: t.id,
  title: t.title,
  description: t.description ?? '',
  status: STATUS_TO_KEY[t.status] ?? 'todo',
  priority: PRIORITY_TO_KEY[t.priority] ?? 'medium',
  assignee: t.assigneeName ?? 'Unassigned',
  assigneeId: t.assigneeId ?? null,
  dueDate: t.dueDate ?? '',
  projectId: t.projectId,
  projectTitle: projectName(t.projectId),
  subtasks: [],
})

// ---- State ----
const tasks = ref<Task[]>([])
const loading = ref(false)
const users = ref<{ id: number; name: string }[]>([])
const filterStatus = ref('ALL')
const filterPriority = ref('ALL')
const filterProject = ref('ALL')
const modalOpen = ref(false)
const editingTask = ref<Task | null>(null)

const statusColors: Record<string, string> = {
  'todo': 'bg-yellow-100 text-yellow-800',
  'in-progress': 'bg-emerald-100 text-emerald-800',
  'review': 'bg-purple-100 text-purple-800',
  'done': 'bg-green-100 text-green-800',
}

const priorityColors: Record<string, string> = {
  'low': 'bg-green-100 text-green-800',
  'medium': 'bg-yellow-100 text-yellow-800',
  'high': 'bg-red-100 text-red-800',
  'urgent': 'bg-red-600 text-white',
}

const filteredTasks = computed(() =>
  tasks.value.filter(t =>
    (filterStatus.value === 'ALL' || t.status === filterStatus.value) &&
    (filterPriority.value === 'ALL' || t.priority === filterPriority.value) &&
    (filterProject.value === 'ALL' || String(t.projectId) === filterProject.value)
  )
)

// ---- Data loading ----
const loadTasks = async () => {
  loading.value = true
  try {
    const data = await useGetTasks()
    tasks.value = (data as any[]).map(mapTask)
  } catch (e) {
    console.error('Failed to load tasks', e)
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  try {
    const data = await useGetUsers()
    users.value = (data as any[])
      .filter(u => u.isActive !== false)
      .map(u => ({ id: u.id, name: u.fullName }))
  } catch (e) {
    console.error('Failed to load users', e)
  }
}

onMounted(async () => {
  await Promise.all([projectsStore.loadProjects(), loadUsers()])
  await loadTasks()
})

// ---- Modal ----
const openCreate = () => {
  editingTask.value = null
  modalOpen.value = true
}

const openEdit = (task: Task) => {
  editingTask.value = task
  modalOpen.value = true
}

const buildPayload = (task: Task) => ({
  projectId: task.projectId,
  assigneeId: task.assigneeId,
  title: task.title,
  description: task.description,
  status: KEY_TO_STATUS[task.status],
  priority: KEY_TO_PRIORITY[task.priority],
  dueDate: task.dueDate,
})

const handleSave = async (task: Task) => {
  const payload = buildPayload(task)
  try {
    if (task.id && tasks.value.some(t => t.id === task.id)) {
      const updated = await useUpdateTask(task.id, payload)
      const idx = tasks.value.findIndex(t => t.id === task.id)
      if (idx !== -1) tasks.value[idx] = mapTask(updated)
    } else {
      const created = await useCreateTask(payload)
      tasks.value.push(mapTask(created))
    }
  } catch (e) {
    console.error('Failed to save task', e)
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('Delete this task? This cannot be undone.')) return
  try {
    await useDeleteTask(id)
    tasks.value = tasks.value.filter(t => t.id !== id)
  } catch (e) {
    console.error('Failed to delete task', e)
  }
}

const toggleComplete = async (task: Task) => {
  const newStatus: Task['status'] = task.status === 'done' ? 'todo' : 'done'
  task.status = newStatus
  try {
    const updated = await useUpdateTask(task.id, {
      ...buildPayload(task),
      status: KEY_TO_STATUS[newStatus],
    })
    const idx = tasks.value.findIndex(t => t.id === task.id)
    if (idx !== -1) tasks.value[idx] = mapTask(updated)
  } catch (e) {
    console.error('Failed to update task status', e)
    task.status = newStatus === 'done' ? 'todo' : 'done'
  }
}

const subtaskProgress = (task: Task) => {
  if (task.subtasks.length === 0) return null
  return `${task.subtasks.filter(s => s.completed).length}/${task.subtasks.length}`
}
</script>

<template>
  <div>
    <div class="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">My Tasks</h1>
        <p class="text-gray-600 mt-1">View and manage your assigned tasks across all projects</p>
      </div>
      <Button @click="openCreate">+ New Task</Button>
    </div>

    <!-- Filters -->
    <div class="bg-white rounded-lg shadow p-4 mb-6">
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex flex-wrap gap-2">
          <button
            v-for="option in ['ALL', 'todo', 'in-progress', 'review', 'done']"
            :key="option"
            @click="filterStatus = option"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              filterStatus === option ? 'bg-emerald-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
            ]"
          >
            {{ option === 'ALL' ? 'All' : STATUS_LABELS[option as Task['status']] }}
          </button>
        </div>
        <div class="flex flex-wrap gap-2 ml-auto">
          <select
            v-model="filterPriority"
            class="h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-700 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
          >
            <option value="ALL">All Priorities</option>
            <option v-for="(label, key) in PRIORITY_LABELS" :key="key" :value="key">
              {{ label }}
            </option>
          </select>
          <select
            v-model="filterProject"
            class="h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-700 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
          >
            <option value="ALL">All Projects</option>
            <option v-for="project in projectsStore.projects" :key="project.id" :value="String(project.id)">
              {{ project.name }}
            </option>
          </select>
        </div>
      </div>
    </div>

    <!-- Tasks List -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <div class="divide-y divide-gray-200">
        <div
          v-for="task in filteredTasks"
          :key="task.id"
          class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
          @click="openEdit(task)"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-start gap-3 min-w-0">
              <input
                type="checkbox"
                :checked="task.status === 'done'"
                @click.stop
                @change="toggleComplete(task)"
                class="mt-1 w-4 h-4 text-emerald-600 border-gray-300 rounded focus:ring-emerald-500"
              />
              <div class="min-w-0">
                <h3 :class="[
                  'font-medium',
                  task.status === 'done' ? 'line-through text-gray-500' : 'text-gray-900'
                ]">
                  {{ task.title }}
                </h3>
                <p class="text-sm text-gray-600 truncate">{{ task.projectTitle }}</p>
                <p class="text-xs text-gray-500 mt-1">
                  Assigned to {{ task.assignee }}
                  <span v-if="subtaskProgress(task)"> · Subtasks {{ subtaskProgress(task) }}</span>
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2 shrink-0 flex-wrap justify-end">
              <span :class="['px-2 py-1 text-xs font-medium rounded-full', priorityColors[task.priority]]">
                {{ PRIORITY_LABELS[task.priority] }}
              </span>
              <span :class="['px-2 py-1 text-xs font-medium rounded-full', statusColors[task.status]]">
                {{ STATUS_LABELS[task.status] }}
              </span>
            </div>
          </div>
          <div class="ml-7 text-sm text-gray-500 mt-2">
            Due: {{ task.dueDate ? formatDate(task.dueDate) : '—' }}
          </div>
        </div>
      </div>

      <div v-if="filteredTasks.length === 0" class="p-12 text-center">
        <p class="text-gray-600">{{ loading ? 'Loading tasks…' : 'No tasks found.' }}</p>
      </div>
    </div>

    <!-- Task Detail / Edit Modal -->
    <TaskDetailModal
      v-model:open="modalOpen"
      :task="editingTask"
      :projects="projectsStore.projects"
      :users="users"
      @save="handleSave"
      @delete="handleDelete"
    />
  </div>
</template>
