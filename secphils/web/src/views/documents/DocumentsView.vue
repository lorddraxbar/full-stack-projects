<script setup lang="ts">
import { ref } from 'vue'

const documents = ref([
  { id: 1, name: 'Process Optimization Report.pdf', project: 'Manufacturing Process Optimization', size: '2.4 MB', uploadedBy: 'John Doe', uploadedDate: '2026-08-10', version: '1.2', tags: ['report', 'optimization'] },
  { id: 2, name: 'Compliance Checklist.xlsx', project: 'Energy Sector Compliance Audit', size: '1.1 MB', uploadedBy: 'Jane Smith', uploadedDate: '2026-08-12', version: '1.0', tags: ['compliance', 'checklist'] },
  { id: 3, name: 'Feasibility Study Final.pdf', project: 'Supply Chain Feasibility Study', size: '5.8 MB', uploadedBy: 'Bob Wilson', uploadedDate: '2026-08-01', version: '2.0', tags: ['feasibility', 'final'] },
  { id: 4, name: 'Site Inspection Photos.zip', project: 'Water Treatment Plant Design', size: '15.2 MB', uploadedBy: 'Alice Brown', uploadedDate: '2026-08-14', version: '1.0', tags: ['photos', 'inspection'] },
  { id: 5, name: 'Energy Assessment Draft.docx', project: 'Renewable Energy Assessment', size: '3.3 MB', uploadedBy: 'John Doe', uploadedDate: '2026-08-15', version: '0.5', tags: ['assessment', 'draft'] },
])

const searchQuery = ref('')
const selectedProject = ref('ALL')

const filteredDocuments = ref(documents.value)

const filterDocuments = () => {
  let result = documents.value

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(d =>
      d.name.toLowerCase().includes(query) ||
      d.project.toLowerCase().includes(query) ||
      d.tags.some(t => t.toLowerCase().includes(query))
    )
  }

  if (selectedProject.value !== 'ALL') {
    result = result.filter(d => d.project === selectedProject.value)
  }

  filteredDocuments.value = result
}

</script>

<template>
  <div>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Documents</h1>
        <p class="text-gray-600 mt-1">View, upload, and manage project documents</p>
      </div>
      <button class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium">
        + Upload Document
      </button>
    </div>

    <!-- Filters -->
    <div class="bg-white rounded-lg shadow p-4 mb-6">
      <div class="flex flex-col sm:flex-row gap-4">
        <div class="flex-1">
          <input
            v-model="searchQuery"
            @input="filterDocuments"
            type="text"
            placeholder="Search documents..."
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <select
          v-model="selectedProject"
          @change="filterDocuments"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="ALL">All Projects</option>
          <option value="Manufacturing Process Optimization">Manufacturing Process Optimization</option>
          <option value="Energy Sector Compliance Audit">Energy Sector Compliance Audit</option>
          <option value="Supply Chain Feasibility Study">Supply Chain Feasibility Study</option>
        </select>
      </div>
    </div>

    <!-- Documents List -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <div class="divide-y divide-gray-200">
        <div
          v-for="doc in filteredDocuments"
          :key="doc.id"
          class="p-6 hover:bg-gray-50 transition-colors"
        >
          <div class="flex items-start justify-between mb-3">
            <div class="flex items-start gap-4">
              <div class="w-10 h-10 bg-red-100 rounded-lg flex items-center justify-center flex-shrink-0">
                <span class="text-red-600 font-bold text-xs">PDF</span>
              </div>
              <div>
                <h3 class="font-medium text-gray-900">{{ doc.name }}</h3>
                <p class="text-sm text-gray-600">{{ doc.project }}</p>
              </div>
            </div>
            <span class="text-sm text-gray-500 flex-shrink-0">{{ doc.size }}</span>
          </div>

          <div class="flex items-center justify-between text-sm">
            <div class="flex items-center gap-4">
              <span class="text-gray-600">By: {{ doc.uploadedBy }}</span>
              <span class="text-gray-600">v{{ doc.version }}</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-gray-500">{{ doc.uploadedDate }}</span>
              <div class="flex gap-1">
                <span
                  v-for="tag in doc.tags"
                  :key="tag"
                  class="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs rounded"
                >
                  {{ tag }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="filteredDocuments.length === 0" class="p-12 text-center">
        <p class="text-gray-600">No documents found matching your criteria.</p>
      </div>
    </div>
  </div>
</template>
