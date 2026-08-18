<script setup lang="ts">
import { ref, computed } from 'vue'
import { Button } from '@/components/ui/button'
import TaskDetailModal from '@/components/TaskDetailModal.vue'
import { useProjectsStore } from '@/stores/projects'
import type { Task } from '@/types/task'
import { STATUS_LABELS, PRIORITY_LABELS } from '@/types/task'

const projectsStore = useProjectsStore()

const tasks = ref<Task[]>([
  {
    id: 1,
    title: 'Review process flow diagrams',
    description: 'Validate the optimized process flow diagrams against the current production baseline before sign-off.',
    status: 'todo',
    priority: 'high',
    assignee: 'Jay Barroga',
    dueDate: '2026-08-20',
    projectId: 1,
    projectTitle: 'Manufacturing Process Optimization',
    subtasks: [
      { id: 1, title: 'Collect latest flow diagrams', completed: true },
      { id: 2, title: 'Cross-check with production data', completed: false },
    ],
  },
  {
    id: 2,
    title: 'Submit compliance documentation',
    description: 'Compile and submit the energy sector compliance package to the regulatory authority.',
    status: 'in-progress',
    priority: 'medium',
    assignee: 'Miguel Santos',
    dueDate: '2026-08-25',
    projectId: 2,
    projectTitle: 'Energy Sector Compliance Audit',
    subtasks: [],
  },
  {
    id: 3,
    title: 'Approve final report',
    description: 'Final review and approval of the supply chain feasibility study report.',
    status: 'review',
    priority: 'low',
    assignee: 'Ana Reyes',
    dueDate: '2026-08-18',
    projectId: 3,
    projectTitle: 'Supply Chain Feasibility Study',
    subtasks: [],
  },
  {
    id: 4,
    title: 'Conduct site inspection',
    description: 'On-site inspection of the water treatment plant design implementation.',
    status: 'done',
    priority: 'high',
    assignee: 'Jay Barroga',
    dueDate: '2026-08-22',
    projectId: 1,
    projectTitle: 'Manufacturing Process Optimization',
    subtasks: [
      { id: 1, title: 'Prepare inspection checklist', completed: true },
      { id: 2, title: 'Photograph key installations', completed: true },
      { id: 3, title: 'File inspection report', completed: true },
    ],
  },
  {
    id: 5,
    title: 'Prepare renewable energy proposal',
    description: 'Draft the renewable energy assessment proposal for client review.',
    status: 'todo',
    priority: 'medium',
    assignee: 'Liza Cruz',
    dueDate: '2026-08-28',
    projectId: 2,
    projectTitle: 'Energy Sector Compliance Audit',
    subtasks: [],
  },
])

const statusColors: Record<string, string> = {
  'todo': 'bg-yellow-100 text-yellow-800',
  'in-progress': 'bg-blue-100 text-blue-800',
  'review': 'bg-purple-100 text-purple-800',
  'done': 'bg-green-100 text-green-800',
}

const priorityColors: Record<string, string> = {
  'low': 'bg-green-100 text-green-800',
  'medium': 'bg-yellow-100 text-yellow-800',
  'high': 'bg-red-100 text-red-800',
  'urgent': 'bg-red-600 text-white',
}

const filterStatus = ref('ALL')
const filteredTasks = computed(() =>
  filterStatus.value === 'ALL'
    ? tasks.value
    : tasks.value.filter(t => t.status === filterStatus.value)
)

// Modal state
const modalOpen = ref(false)
const editingTask = ref<Task | null>(null)

const openCreate = () => {
  editingTask.value = null
  modalOpen.value = true
}

const openEdit = (task: Task) => {
  editingTask.value = task
  modalOpen.value = true
}

const handleSave = (task: Task) => {
  const index = tasks.value.findIndex(t => t.id === task.id)
  if (index !== -1) {
    tasks.value[index] = task
  } else {
    tasks.value.push(task)
  }
}

const handleDelete = (id: number) => {
  if (confirm('Delete this task? This cannot be undone.')) {
    tasks.value = tasks.value.filter(t => t.id !== id)
  }
}

const toggleComplete = (task: Task) => {
  task.status = task.status === 'done' ? 'todo' : 'done'
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
      <div class="flex flex-wrap gap-2">
        <button
          v-for="option in ['ALL', 'todo', 'in-progress', 'review', 'done']"
          :key="option"
          @click="filterStatus = option"
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            filterStatus === option ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
          ]"
        >
          {{ option === 'ALL' ? 'All' : STATUS_LABELS[option as Task['status']] }}
        </button>
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
                class="mt-1 w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
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
            Due: {{ task.dueDate }}
          </div>
        </div>
      </div>

      <div v-if="filteredTasks.length === 0" class="p-12 text-center">
        <p class="text-gray-600">No tasks found.</p>
      </div>
    </div>

    <!-- Task Detail / Edit Modal -->
    <TaskDetailModal
      v-model:open="modalOpen"
      :task="editingTask"
      :projects="projectsStore.projects"
      @save="handleSave"
      @delete="handleDelete"
    />
  </div>
</template>
