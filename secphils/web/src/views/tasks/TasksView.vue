<script setup lang="ts">
import { ref } from 'vue'

const tasks = ref([
  { id: 1, title: 'Review process flow diagrams', project: 'Manufacturing Process Optimization', dueDate: '2026-08-20', priority: 'High', status: 'Pending' },
  { id: 2, title: 'Submit compliance documentation', project: 'Energy Sector Compliance Audit', dueDate: '2026-08-25', priority: 'Medium', status: 'In Progress' },
  { id: 3, title: 'Approve final report', project: 'Supply Chain Feasibility Study', dueDate: '2026-08-18', priority: 'Low', status: 'Pending' },
  { id: 4, title: 'Conduct site inspection', project: 'Water Treatment Plant Design', dueDate: '2026-08-22', priority: 'High', status: 'Completed' },
  { id: 5, title: 'Prepare renewable energy proposal', project: 'Renewable Energy Assessment', dueDate: '2026-08-28', priority: 'Medium', status: 'Pending' },
])

const statusColors: Record<string, string> = {
  'Pending': 'bg-yellow-100 text-yellow-800',
  'In Progress': 'bg-blue-100 text-blue-800',
  'Completed': 'bg-green-100 text-green-800',
}

const priorityColors: Record<string, string> = {
  'High': 'bg-red-100 text-red-800',
  'Medium': 'bg-yellow-100 text-yellow-800',
  'Low': 'bg-green-100 text-green-800',
}

const filterStatus = ref('ALL')
const filteredTasks = ref(tasks.value)

const filterTasks = () => {
  filteredTasks.value = filterStatus.value === 'ALL'
    ? tasks.value
    : tasks.value.filter(t => t.status === filterStatus.value)
}
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">My Tasks</h1>
      <p class="text-gray-600 mt-1">View and manage your assigned tasks across all projects</p>
    </div>

    <!-- Filters -->
    <div class="bg-white rounded-lg shadow p-4 mb-6">
      <div class="flex gap-2">
        <button
          @click="filterStatus = 'ALL'; filterTasks()"
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            filterStatus === 'ALL' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
          ]"
        >
          All
        </button>
        <button
          @click="filterStatus = 'Pending'; filterTasks()"
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            filterStatus === 'Pending' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
          ]"
        >
          Pending
        </button>
        <button
          @click="filterStatus = 'In Progress'; filterTasks()"
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            filterStatus === 'In Progress' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
          ]"
        >
          In Progress
        </button>
        <button
          @click="filterStatus = 'Completed'; filterTasks()"
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            filterStatus === 'Completed' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
          ]"
        >
          Completed
        </button>
      </div>
    </div>

    <!-- Tasks List -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <div class="divide-y divide-gray-200">
        <div
          v-for="task in filteredTasks"
          :key="task.id"
          class="p-6 hover:bg-gray-50 transition-colors"
        >
          <div class="flex items-start justify-between mb-2">
            <div class="flex items-start gap-3">
              <input
                type="checkbox"
                :checked="task.status === 'Completed'"
                class="mt-1 w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <div>
                <h3 :class="[
                  'font-medium',
                  task.status === 'Completed' ? 'line-through text-gray-500' : 'text-gray-900'
                ]">
                  {{ task.title }}
                </h3>
                <p class="text-sm text-gray-600">{{ task.project }}</p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <span :class="['px-2 py-1 text-xs font-medium rounded-full', priorityColors[task.priority]]">
                {{ task.priority }}
              </span>
              <span :class="['px-2 py-1 text-xs font-medium rounded-full', statusColors[task.status]]">
                {{ task.status }}
              </span>
            </div>
          </div>
          <div class="ml-7 text-sm text-gray-500">
            Due: {{ task.dueDate }}
          </div>
        </div>
      </div>

      <div v-if="filteredTasks.length === 0" class="p-12 text-center">
        <p class="text-gray-600">No tasks found.</p>
      </div>
    </div>
  </div>
</template>
