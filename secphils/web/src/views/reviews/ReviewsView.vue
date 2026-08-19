<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRole } from '@/composables/useRole'
import { useGetReviews, useSubmitReview, useUpdateReviewStatus, useGetProjects } from '@/services/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'

interface Review {
  id: number
  projectId: number
  customerId: number
  customerName: string
  rating: number
  title: string
  body: string
  status: string
  createdAt: string
}

const { isClient } = useRole()
const isUser = computed(() => !isClient.value)
const isCustomer = computed(() => isClient.value)

const reviews = ref<Review[]>([])
const projects = ref<{ id: number; name: string }[]>([])
const loading = ref(true)
const error = ref('')
const selectedStatus = ref('ALL')

const projectById = (id: number) => projects.value.find(p => p.id === id)

const filteredReviews = computed(() => {
  const list = selectedStatus.value === 'ALL'
    ? reviews.value
    : reviews.value.filter(r => r.status === selectedStatus.value)
  return [...list].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [revRes, projRes] = await Promise.all([useGetReviews(), useGetProjects()])
    reviews.value = (revRes as Review[]) || []
    const projList = Array.isArray(projRes) ? projRes : ((projRes as any)?.content ?? [])
    projects.value = (projList as { id: number; name: string }[]).map(p => ({ id: p.id, name: p.name }))
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    error.value = err.response?.data?.message || err.message || 'Failed to load reviews'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const getStarDisplay = (rating: number) => '★'.repeat(rating) + '☆'.repeat(5 - rating)

const formatDate = (dateStr: string) =>
  new Date(dateStr).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })

const statusColors: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
}

const statusLabels: Record<string, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

// --- Create review (customers) ---
const showForm = ref(false)
const stars = [1, 2, 3, 4, 5]
const form = ref({
  projectId: '' as string,
  rating: 0,
  title: '',
  body: '',
})
const saving = ref(false)
const saveError = ref('')

// Projects the customer hasn't reviewed yet (backend enforces one review per project)
const projectsWithoutReview = computed(() => {
  const reviewed = new Set(reviews.value.map(r => r.projectId))
  return projects.value.filter(p => !reviewed.has(p.id))
})

function openForm() {
  form.value = { projectId: '', rating: 0, title: '', body: '' }
  saveError.value = ''
  showForm.value = true
}

async function submitReview() {
  if (!form.value.projectId) {
    saveError.value = 'Select a project.'
    return
  }
  if (!form.value.rating) {
    saveError.value = 'Select a star rating.'
    return
  }
  if (!form.value.title.trim()) {
    saveError.value = 'A review title is required.'
    return
  }
  saving.value = true
  saveError.value = ''
  try {
    await useSubmitReview({
      projectId: Number(form.value.projectId),
      rating: form.value.rating,
      title: form.value.title.trim(),
      body: form.value.body.trim() || null,
    })
    showForm.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    saveError.value = err.response?.data?.message || err.message || 'Failed to submit review'
  } finally {
    saving.value = false
  }
}

// --- user approve / reject ---
async function setStatus(id: number, status: string) {
  try {
    await useUpdateReviewStatus(id, status)
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    alert(err.response?.data?.message || err.message || 'Failed to update review status')
  }
}
</script>

<template>
  <div>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Reviews &amp; Ratings</h1>
        <p class="text-gray-600 mt-1">Customer reviews and ratings</p>
      </div>
      <Button v-if="isCustomer && projectsWithoutReview.length > 0" @click="openForm">
        + Submit Review
      </Button>
    </div>

    <!-- Filters -->
    <Card class="mb-6">
      <CardContent class="p-4">
        <div class="flex flex-col sm:flex-row gap-4">
          <select
            v-model="selectedStatus"
            class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">All Status</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
      </CardContent>
    </Card>

    <p v-if="error" class="mb-4 text-sm text-red-600">{{ error }}</p>

    <!-- Reviews List -->
    <div class="grid gap-6">
      <Card
        v-for="review in filteredReviews"
        :key="review.id"
        class="hover:shadow-md transition-shadow"
      >
        <CardHeader>
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <CardTitle class="text-lg">{{ review.title }}</CardTitle>
            <Badge :class="statusColors[review.status] || 'bg-gray-100 text-gray-800'">
              {{ statusLabels[review.status] || review.status }}
            </Badge>
          </div>
          <div class="flex items-center gap-2 text-sm text-muted-foreground flex-wrap">
            <span>{{ review.customerName || '—' }}</span>
            <span>•</span>
            <span>{{ projectById(review.projectId)?.name || 'Project #' + review.projectId }}</span>
            <span>•</span>
            <span>{{ formatDate(review.createdAt) }}</span>
          </div>
        </CardHeader>
        <CardContent>
          <div class="flex items-center gap-2 mb-3">
            <span class="text-yellow-400 text-lg">{{ getStarDisplay(review.rating) }}</span>
            <span class="text-sm text-muted-foreground">({{ review.rating }}/5)</span>
          </div>
          <p v-if="review.body" class="text-gray-700">{{ review.body }}</p>
          <div v-if="isUser" class="flex gap-2 mt-4">
            <button
              @click="setStatus(review.id, 'APPROVED')"
              class="px-3 py-1.5 text-sm font-medium rounded-lg bg-green-600 text-white hover:bg-green-700"
            >
              Approve
            </button>
            <button
              @click="setStatus(review.id, 'REJECTED')"
              class="px-3 py-1.5 text-sm font-medium rounded-lg bg-red-600 text-white hover:bg-red-700"
            >
              Reject
            </button>
          </div>
        </CardContent>
      </Card>
    </div>

    <div v-if="!loading && filteredReviews.length === 0" class="p-12 text-center">
      <p class="text-gray-600">No reviews found matching your criteria.</p>
    </div>
    <div v-if="loading" class="p-12 text-center">
      <p class="text-gray-500">Loading reviews…</p>
    </div>

    <!-- Submit Review dialog -->
    <Dialog v-model:open="showForm">
      <DialogContent class="max-w-2xl max-h-[90vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>Submit Review</DialogTitle>
          <DialogDescription>Share your experience with a completed project</DialogDescription>
        </DialogHeader>

        <div class="flex-1 overflow-y-auto space-y-6">
          <div class="space-y-2">
            <Label for="reviewProject">Project</Label>
            <Select v-model="form.projectId">
              <SelectTrigger id="reviewProject">
                <SelectValue placeholder="Select a project…" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem
                    v-for="p in projectsWithoutReview"
                    :key="p.id"
                    :value="String(p.id)"
                  >
                    {{ p.name }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
            <p v-if="projectsWithoutReview.length === 0" class="text-sm text-muted-foreground">
              You have already submitted a review for all your projects.
            </p>
          </div>

          <Separator />

          <div class="space-y-2">
            <Label>Overall Rating</Label>
            <div class="flex items-center gap-2">
              <div class="flex gap-1">
                <button
                  v-for="star in stars"
                  :key="star"
                  type="button"
                  @click="form.rating = star"
                  class="text-2xl focus:outline-none transition-colors"
                >
                  <span :class="star <= form.rating ? 'text-yellow-400' : 'text-gray-300 hover:text-yellow-300'">★</span>
                </button>
              </div>
              <Badge :class="form.rating > 0 ? 'bg-yellow-100 text-yellow-800' : 'bg-gray-100 text-gray-800'">
                {{ form.rating > 0 ? form.rating + '/5' : 'Select rating' }}
              </Badge>
            </div>
          </div>

          <Separator />

          <div class="space-y-2">
            <Label for="reviewTitle">Review Title</Label>
            <Input
              id="reviewTitle"
              v-model="form.title"
              placeholder="Summarize your experience"
            />
          </div>

          <div class="space-y-2">
            <Label for="reviewBody">Review Body</Label>
            <Textarea
              id="reviewBody"
              v-model="form.body"
              placeholder="Tell us about your experience…"
              rows="5"
            />
          </div>
        </div>

        <p v-if="saveError" class="text-sm text-red-600">{{ saveError }}</p>

        <DialogFooter>
          <Button variant="outline" @click="showForm = false">Cancel</Button>
          <Button @click="submitReview" :disabled="saving">
            {{ saving ? 'Submitting…' : 'Submit Review' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
