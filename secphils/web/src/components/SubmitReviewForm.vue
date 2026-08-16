<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'

const props = defineProps<{
  open: boolean
  review?: Review | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  save: [review: Review]
  delete: [id: number]
}>()

export interface Review {
  id: number
  customerId: number
  customerName: string
  projectId: number
  projectTitle: string
  rating: number
  title: string
  body: string
  status: 'pending' | 'approved' | 'rejected'
  createdAt: string
}

const dialogOpen = ref(props.open)
const editingReview = ref<Review | null>(null)
const selectedStars = ref(0)
const isHovering = ref(0)

watch(() => props.open, (val) => {
  dialogOpen.value = val
  if (val && props.review) {
    editingReview.value = JSON.parse(JSON.stringify(props.review))
    selectedStars.value = props.review.rating
  }
})

watch(dialogOpen, (val) => {
  emit('update:open', val)
})

const statusColors: Record<string, string> = {
  'pending': 'bg-yellow-100 text-yellow-800',
  'approved': 'bg-green-100 text-green-800',
  'rejected': 'bg-red-100 text-red-800',
}

const statusLabels: Record<string, string> = {
  'pending': 'Pending',
  'approved': 'Approved',
  'rejected': 'Rejected',
}

const stars = [1, 2, 3, 4, 5]

const handleStarClick = (star: number) => {
  selectedStars.value = star
  if (editingReview.value) {
    editingReview.value.rating = star
  }
}

const handleStarHover = (star: number) => {
  isHovering.value = star
}

const handleStarLeave = () => {
  isHovering.value = 0
}

const getStarClass = (star: number) => {
  const rating = isHovering.value || selectedStars.value
  if (star <= rating) {
    return 'text-yellow-400 hover:text-yellow-500'
  }
  return 'text-gray-300 hover:text-yellow-300'
}

const getStarLabel = (rating: number) => {
  const labels = ['', 'Poor', 'Fair', 'Good', 'Very Good', 'Excellent']
  return labels[rating] || ''
}

const handleSave = () => {
  if (editingReview.value) {
    emit('save', editingReview.value)
    dialogOpen.value = false
  }
}

const handleClose = () => {
  dialogOpen.value = false
}

const handleDelete = () => {
  if (editingReview.value) {
    emit('delete', editingReview.value.id)
    dialogOpen.value = false
  }
}
</script>

<template>
  <Dialog v-model:open="dialogOpen">
    <DialogContent class="max-w-2xl max-h-[90vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>{{ editingReview ? 'Edit Review' : 'Submit Review' }}</DialogTitle>
        <DialogDescription>
          {{ editingReview ? 'Update review details' : 'Share your experience with this project' }}
        </DialogDescription>
      </DialogHeader>

      <div v-if="editingReview" class="flex-1 overflow-y-auto space-y-6">
        <!-- Customer & Project -->
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="customer">Customer</Label>
            <Input
              id="customer"
              v-model="editingReview.customerName"
              placeholder="Customer name"
            />
          </div>

          <div class="space-y-2">
            <Label for="project">Project</Label>
            <Select v-model="editingReview.projectId">
              <SelectTrigger>
                <SelectValue placeholder="Select a project..." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value="1">Manufacturing Process Optimization</SelectItem>
                  <SelectItem value="2">Energy Sector Compliance Audit</SelectItem>
                  <SelectItem value="3">Supply Chain Feasibility Study</SelectItem>
                  <SelectItem value="4">Water Treatment Plant Design</SelectItem>
                  <SelectItem value="5">Renewable Energy Assessment</SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
        </div>

        <Separator />

        <!-- Star Rating -->
        <div class="space-y-2">
          <Label>Overall Rating</Label>
          <div class="flex items-center gap-2">
            <div class="flex gap-1">
              <button
                v-for="star in stars"
                :key="star"
                type="button"
                @click="handleStarClick(star)"
                @mouseenter="handleStarHover(star)"
                @mouseleave="handleStarLeave"
                class="text-2xl focus:outline-none transition-colors"
              >
                <span :class="getStarClass(star)">★</span>
              </button>
            </div>
            <Badge :class="selectedStars > 0 ? 'bg-yellow-100 text-yellow-800' : 'bg-gray-100 text-gray-800'">
              {{ selectedStars > 0 ? getStarLabel(selectedStars) : 'Select rating' }}
            </Badge>
          </div>
        </div>

        <Separator />

        <!-- Review Title & Body -->
        <div class="space-y-2">
          <Label for="reviewTitle">Review Title</Label>
          <Input
            id="reviewTitle"
            v-model="editingReview.title"
            placeholder="Summarize your experience"
          />
        </div>

        <div class="space-y-2">
          <Label for="reviewBody">Review Body</Label>
          <Textarea
            id="reviewBody"
            v-model="editingReview.body"
            placeholder="Tell us about your experience..."
            rows="5"
          />
        </div>

        <Separator />

        <!-- Status -->
        <div class="space-y-2">
          <Label>Status</Label>
          <Badge :class="statusColors[editingReview.status]">
            {{ statusLabels[editingReview.status] }}
          </Badge>
          <p class="text-sm text-muted-foreground">
            Reviews require provider approval before appearing on the public marketing landing page.
          </p>
        </div>
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
            Save Review
          </Button>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
