<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { ScrollArea } from '@/components/ui/scroll-area'

const props = defineProps<{
  open: boolean
  document?: Document | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  save: [doc: Document]
  delete: [id: number]
}>()

export interface Document {
  id: number
  title: string
  description: string
  type: 'deliverable' | 'report' | 'contract' | 'drawing' | 'other'
  version: string
  fileSize: string
  uploadedBy: string
  uploadedAt: string
  status: 'draft' | 'pending-review' | 'approved' | 'archived'
  comments: Comment[]
  versions: DocumentVersion[]
  tags: string[]
}

export interface Comment {
  id: number
  author: string
  content: string
  createdAt: string
  replies?: Comment[]
}

export interface DocumentVersion {
  id: number
  version: string
  uploadedAt: string
  uploadedBy: string
  fileSize: string
  changeSummary: string
}

const dialogOpen = ref(props.open)
const editingDoc = ref<Document | null>(null)
const activeTab = ref('preview')

watch(() => props.open, (val) => {
  dialogOpen.value = val
  if (val && props.document) {
    editingDoc.value = JSON.parse(JSON.stringify(props.document))
  }
})

watch(dialogOpen, (val) => {
  emit('update:open', val)
})

const statusColors: Record<string, string> = {
  'draft': 'bg-gray-100 text-gray-800',
  'pending-review': 'bg-yellow-100 text-yellow-800',
  'approved': 'bg-green-100 text-green-800',
  'archived': 'bg-blue-100 text-blue-800',
}

const statusLabels: Record<string, string> = {
  'draft': 'Draft',
  'pending-review': 'Pending Review',
  'approved': 'Approved',
  'archived': 'Archived',
}

const typeLabels: Record<string, string> = {
  'deliverable': 'Deliverable',
  'report': 'Report',
  'contract': 'Contract',
  'drawing': 'Drawing',
  'other': 'Other',
}

const handleSave = () => {
  if (editingDoc.value) {
    emit('save', editingDoc.value)
    dialogOpen.value = false
  }
}

const handleClose = () => {
  dialogOpen.value = false
}

const handleDelete = () => {
  if (editingDoc.value) {
    emit('delete', editingDoc.value.id)
    dialogOpen.value = false
  }
}

const addComment = () => {
  if (!editingDoc.value) return
  editingDoc.value.comments.push({
    id: Date.now(),
    author: 'Current User',
    content: '',
    createdAt: new Date().toISOString(),
  })
}

const commentCount = (doc: Document) => {
  return doc.comments.length
}

const versionCount = (doc: Document) => {
  return doc.versions.length
}
</script>

<template>
  <Dialog v-model:open="dialogOpen">
    <DialogContent class="max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>{{ editingDoc ? 'Edit Document' : 'New Document' }}</DialogTitle>
        <DialogDescription>
          {{ editingDoc ? 'Update document details' : 'Upload a new document' }}
        </DialogDescription>
      </DialogHeader>

      <div v-if="editingDoc" class="flex-1 overflow-y-auto">
        <Tabs v-model="activeTab" class="w-full">
          <TabsList class="grid w-full grid-cols-3">
            <TabsTrigger value="preview">Preview</TabsTrigger>
            <TabsTrigger value="details">Details</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
          </TabsList>

          <!-- Preview Tab -->
          <TabsContent value="preview" class="space-y-4">
            <div class="aspect-video bg-gray-100 rounded-lg flex items-center justify-center">
              <div class="text-center">
                <div class="text-4xl mb-2">📄</div>
                <p class="text-muted-foreground">{{ editingDoc.title }}</p>
                <p class="text-sm text-muted-foreground">{{ editingDoc.fileSize }}</p>
              </div>
            </div>

            <div class="flex gap-2">
              <Button variant="outline">Download</Button>
              <Button variant="outline">Print</Button>
              <Button variant="outline">Share</Button>
            </div>
          </TabsContent>

          <!-- Details Tab -->
          <TabsContent value="details" class="space-y-4">
            <div class="space-y-2">
              <Label for="docTitle">Document Title</Label>
              <Input
                id="docTitle"
                v-model="editingDoc.title"
              />
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label>Type</Label>
                <Badge :class="statusColors[editingDoc.type]">
                  {{ typeLabels[editingDoc.type] }}
                </Badge>
              </div>

              <div class="space-y-2">
                <Label>Status</Label>
                <Badge :class="statusColors[editingDoc.status]">
                  {{ statusLabels[editingDoc.status] }}
                </Badge>
              </div>
            </div>

            <Separator />

            <!-- Comments Section -->
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <Label>Comments ({{ commentCount(editingDoc) }})</Label>
                <Button variant="outline" size="sm" @click="addComment">
                  + Add Comment
                </Button>
              </div>

              <ScrollArea class="h-64 border rounded-lg p-4">
                <div v-if="editingDoc.comments.length === 0" class="text-center text-muted-foreground py-8">
                  No comments yet.
                </div>

                <div v-for="comment in editingDoc.comments" :key="comment.id" class="mb-4">
                  <div class="flex items-start gap-3">
                    <div class="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-sm font-medium">
                      {{ comment.author.charAt(0) }}
                    </div>
                    <div class="flex-1">
                      <div class="flex items-center gap-2">
                        <span class="font-medium text-sm">{{ comment.author }}</span>
                        <span class="text-xs text-muted-foreground">{{ new Date(comment.createdAt).toLocaleDateString() }}</span>
                      </div>
                      <Textarea
                        v-model="comment.content"
                        placeholder="Write a comment..."
                        class="mt-1"
                        rows="2"
                      />
                    </div>
                  </div>
                </div>
              </ScrollArea>
            </div>
          </TabsContent>

          <!-- History Tab -->
          <TabsContent value="history" class="space-y-4">
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <Label>Version History ({{ versionCount(editingDoc) }})</Label>
              </div>

              <div v-for="version in editingDoc.versions" :key="version.id" class="p-3 border rounded-lg">
                <div class="flex items-center justify-between mb-1">
                  <span class="font-medium">v{{ version.version }}</span>
                  <Badge v-if="version.version === editingDoc.version" variant="secondary">Current</Badge>
                </div>
                <p class="text-sm text-muted-foreground">
                  Uploaded by {{ version.uploadedBy }} on {{ new Date(version.uploadedAt).toLocaleDateString() }}
                </p>
                <p class="text-sm mt-1">{{ version.changeSummary }}</p>
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </div>

      <Separator />

      <DialogFooter class="flex flex-col sm:flex-row gap-2 sm:justify-between">
        <Button variant="destructive" @click="handleDelete">
          Delete
        </Button>

        <div class="flex gap-2">
          <Button variant="outline" @click="handleClose">
            Cancel
          </Button>
          <Button @click="handleSave">
            Save Document
          </Button>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
