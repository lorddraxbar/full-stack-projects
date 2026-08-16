<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'

const props = defineProps<{
  open: boolean
  task?: Task | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  save: [task: Task]
  delete: [id: number]
}>()

export interface Task {
  id: number
  title: string
  description: string
  status: 'todo' | 'in-progress' | 'review' | 'done'
  priority: 'low' | 'medium' | 'high' | 'urgent'
  assignee: string
  dueDate: string
  projectId: number
  projectTitle: string
  tags: string[]
  subtasks: Subtask[]
}

export interface Subtask {
  id: number
  title: string
  completed: boolean
}

const dialogOpen = ref(props.open)
const editingTask = ref<Task | null>(null)

watch(() => props.open, (val) => {
  dialogOpen.value = val
  if (val && props.task) {
    editingTask.value = JSON.parse(JSON.stringify(props.task)) // Deep copy
  }
})

watch(dialogOpen, (val) => {
  emit('update:open', val)
})

const statusLabels: Record<string, string> = {
  'todo': 'To Do',
  'in-progress': 'In Progress',
  'review': 'Review',
  'done': 'Done',
}

const priorityLabels: Record<string, string> = {
  'low': 'Low',
  'medium': 'Medium',
  'high': 'High',
  'urgent': 'Urgent',
}

const addSubtask = () => {
  if (!editingTask.value) return
  const newSubtask: Subtask = {
    id: Date.now(),
    title: '',
    completed: false,
  }
  editingTask.value.subtasks.push(newSubtask)
}

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
  if (editingTask.value) {
    emit('save', editingTask.value)
    dialogOpen.value = false
  }
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
        <DialogTitle>{{ editingTask ? 'Edit Task' : 'New Task' }}</DialogTitle>
        <DialogDescription>
          {{ editingTask ? 'Update task details' : 'Create a new task' }}
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
            <Label for="assignee">Assignee *</Label>
            <Input
              id="assignee"
              v-model="editingTask.assignee"
              placeholder="Assigned to"
            />
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
        <Button variant="destructive" @click="handleDelete">
          Delete
        </Button>

        <div class="flex gap-2">
          <Button variant="outline" @click="handleClose">
            Cancel
          </Button>
          <Button @click="handleSave">
            Save Task
          </Button>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
