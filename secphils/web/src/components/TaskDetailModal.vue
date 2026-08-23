<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import type { AcceptableValue } from 'reka-ui'
import type { Task, Subtask } from '@/types/task'
import { STATUS_LABELS, PRIORITY_LABELS } from '@/types/task'

interface ProjectOption {
  id: number
  name: string
}

interface UserOption {
  id: number
  name: string
}

const props = withDefaults(defineProps<{
  open: boolean
  task?: Task | null
  projects?: ProjectOption[]
  users?: UserOption[]
  /** Show the Delete button (admins only). */
  allowDelete?: boolean
  /** Lock the assignee select to the current assignee (non-admins). */
  lockAssignee?: boolean
}>(), {
  allowDelete: true,
  lockAssignee: false,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  save: [task: Task]
  delete: [id: number]
}>()

const dialogOpen = ref(props.open)
const editingTask = ref<Task | null>(null)
const isCreateMode = ref(false)
const saveError = ref('')

const newTask = (): Task => ({
  id: Date.now(),
  title: '',
  description: '',
  status: 'todo',
  priority: 'medium',
  assignee: '',
  assigneeId: null,
  dueDate: '',
  projectId: props.projects?.[0]?.id ?? 0,
  projectTitle: props.projects?.[0]?.name ?? '',
  subtasks: [],
})

watch(() => props.open, (val) => {
  dialogOpen.value = val
  if (val) {
    saveError.value = ''
    if (props.task) {
      isCreateMode.value = false
      const copy = JSON.parse(JSON.stringify(props.task)) as Task // Deep copy
      if (!Array.isArray(copy.subtasks)) copy.subtasks = []
      editingTask.value = copy
    } else {
      isCreateMode.value = true
      editingTask.value = newTask()
    }
  }
})

watch(dialogOpen, (val) => {
  emit('update:open', val)
})

const statusLabels = STATUS_LABELS

const priorityLabels = PRIORITY_LABELS

const onProjectChange = (value: AcceptableValue) => {
  if (!editingTask.value || value === null || value === undefined) return
  const project = props.projects?.find(p => p.id === Number(value))
  if (project) {
    editingTask.value.projectId = project.id
    editingTask.value.projectTitle = project.name
  }
}

const onAssigneeChange = (value: AcceptableValue) => {
  if (!editingTask.value || value === null || value === undefined) return
  const userId = Number(value)
  const user = props.users?.find(u => u.id === userId)
  editingTask.value.assigneeId = userId
  editingTask.value.assignee = user?.name ?? ''
}

const addSubtask = () => {
  if (!editingTask.value) return
  const newSubtask: Subtask = {
    // Unique even within the same millisecond.
    id: Date.now() + Math.floor(Math.random() * 1e6),
    title: '',
    completed: false,
  }
  editingTask.value.subtasks.push(newSubtask)
}

// Assignee options: everyone when editable; only the current assignee when
// locked (non-admins can't reassign). Non-admins can't list users (403), so
// fall back to the task's own assignee name.
const assigneeOptions = computed<UserOption[]>(() => {
  if (!props.lockAssignee) return props.users ?? []
  const t = editingTask.value
  if (!t?.assigneeId) return []
  const current = (props.users ?? []).find(u => u.id === t.assigneeId)
  return [current ?? { id: t.assigneeId, name: t.assignee || `User #${t.assigneeId}` }]
})

const assigneeModelValue = computed(() =>
  editingTask.value?.assigneeId ? String(editingTask.value.assigneeId) : ''
)

const removeSubtask = (subtaskId: number) => {
  if (!editingTask.value) return
  editingTask.value.subtasks = editingTask.value.subtasks.filter(s => s.id !== subtaskId)
}

const toggleSubtask = (subtaskId: number) => {
  if (!editingTask.value) return
  const subtask = editingTask.value.subtasks.find(s => s.id === subtaskId)
  if (subtask) {
    subtask.completed = !subtask.completed
  }
}

const handleSave = () => {
  if (!editingTask.value) return
  if (!editingTask.value.title.trim()) {
    saveError.value = 'Task title is required.'
    return
  }
  // Non-admins creating a task leave the assignee blank — the backend
  // defaults it to themselves.
  if (!props.lockAssignee && !editingTask.value.assigneeId) {
    saveError.value = 'Assignee is required.'
    return
  }
  if (!editingTask.value.dueDate) {
    saveError.value = 'Due date is required.'
    return
  }
  emit('save', editingTask.value)
  dialogOpen.value = false
}

const handleClose = () => {
  dialogOpen.value = false
}

const handleDelete = () => {
  if (editingTask.value) {
    emit('delete', editingTask.value.id)
    dialogOpen.value = false
  }
}

const completedSubtasks = (task: Task) => {
  return task.subtasks.filter(s => s.completed).length
}

const totalSubtasks = (task: Task) => {
  return task.subtasks.length
}
</script>

<template>
  <Dialog v-model:open="dialogOpen">
    <DialogContent class="max-w-2xl max-h-[90vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>{{ isCreateMode ? 'New Task' : 'Edit Task' }}</DialogTitle>
        <DialogDescription>
          {{ isCreateMode ? 'Create a new task' : 'Update task details' }}
        </DialogDescription>
      </DialogHeader>

      <div v-if="editingTask" class="flex-1 overflow-y-auto space-y-6">
        <!-- Basic Info -->
        <div class="space-y-4">
          <div class="space-y-2">
            <Label for="taskTitle">Task Title *</Label>
            <Input
              id="taskTitle"
              v-model="editingTask.title"
              placeholder="Enter task title"
            />
          </div>

          <div class="space-y-2">
            <Label for="taskDescription">Description</Label>
            <Textarea
              id="taskDescription"
              v-model="editingTask.description"
              placeholder="Describe the task"
              rows="3"
            />
          </div>

          <div class="space-y-2">
            <Label>Project</Label>
            <Select :model-value="String(editingTask.projectId)" @update:model-value="onProjectChange">
              <SelectTrigger>
                <SelectValue placeholder="Select a project" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem v-for="project in projects" :key="project.id" :value="String(project.id)">
                    {{ project.name }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
        </div>

        <Separator />

        <!-- Status & Priority -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label>Status</Label>
            <Select v-model="editingTask.status">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem v-for="(label, key) in statusLabels" :key="key" :value="key">
                    {{ label }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>

          <div class="space-y-2">
            <Label>Priority</Label>
            <Select v-model="editingTask.priority">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem v-for="(label, key) in priorityLabels" :key="key" :value="key">
                    {{ label }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
        </div>

        <Separator />

        <!-- Assignee & Due Date -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="assignee">Assignee {{ props.lockAssignee ? '' : '*' }}</Label>
            <Select
              :model-value="assigneeModelValue"
              :disabled="props.lockAssignee"
              @update:model-value="onAssigneeChange"
            >
              <SelectTrigger :class="props.lockAssignee ? 'opacity-70' : ''">
                <SelectValue placeholder="Select an assignee" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem v-for="user in assigneeOptions" :key="user.id" :value="String(user.id)">
                    {{ user.name }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>

          <div class="space-y-2">
            <Label for="dueDate">Due Date *</Label>
            <Input
              id="dueDate"
              v-model="editingTask.dueDate"
              type="date"
            />
          </div>
        </div>

        <Separator />

        <!-- Subtasks -->
        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <Label>Subtasks</Label>
            <Button variant="outline" size="sm" @click="addSubtask">
              + Add Subtask
            </Button>
          </div>

          <div v-if="editingTask.subtasks.length === 0" class="text-sm text-muted-foreground text-center py-4">
            No subtasks yet. Click "Add Subtask" to create one.
          </div>

          <div v-for="subtask in editingTask.subtasks" :key="subtask.id" class="flex items-center gap-2">
            <input
              type="checkbox"
              :checked="subtask.completed"
              @change="toggleSubtask(subtask.id)"
              class="h-4 w-4 rounded border-gray-300"
            />
            <Input
              v-model="subtask.title"
              placeholder="Subtask title"
              class="flex-1"
            />
            <Button variant="ghost" size="sm" @click="removeSubtask(subtask.id)">
              ✕
            </Button>
          </div>

          <div v-if="editingTask.subtasks.length > 0" class="text-sm text-muted-foreground">
            {{ completedSubtasks(editingTask) }}/{{ totalSubtasks(editingTask) }} completed
          </div>
        </div>
      </div>

      <Separator />

      <!-- Footer -->
      <DialogFooter class="flex flex-col sm:flex-row gap-2 sm:justify-between">
        <Button v-if="!isCreateMode && allowDelete" variant="destructive" @click="handleDelete">
          Delete
        </Button>
        <span v-else />

        <div class="flex flex-col items-stretch gap-2 sm:items-end">
          <p v-if="saveError" class="text-sm text-red-600 sm:text-right">{{ saveError }}</p>
          <div class="flex gap-2">
            <Button variant="outline" @click="handleClose">
              Cancel
            </Button>
            <Button @click="handleSave">
              {{ isCreateMode ? 'Create Task' : 'Save Task' }}
            </Button>
          </div>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
