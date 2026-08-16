<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import SubmitReviewForm from '@/components/SubmitReviewForm.vue'

interface Review {
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

const reviews = ref<Review[]>([
  {
    id: 1,
    customerId: 1,
    customerName: 'ABC Manufacturing',
    projectId: 1,
    projectTitle: 'Manufacturing Process Optimization',
    rating: 5,
    title: 'Exceptional service and results',
    body: 'The team delivered beyond our expectations. The process optimization reduced our manufacturing time by 30%. Highly recommended for any manufacturing company looking to improve efficiency.',
    status: 'approved',
    createdAt: '2026-07-15T10:00:00Z',
  },
  {
    id: 2,
    customerId: 2,
    customerName: 'XYZ Energy Corp',
    projectId: 2,
    projectTitle: 'Energy Sector Compliance Audit',
    rating: 4,
    title: 'Thorough and professional audit',
    body: 'The compliance audit was comprehensive and well-documented. The team identified several areas for improvement that we had missed. Only minor delay in delivery, but overall excellent work.',
    status: 'approved',
    createdAt: '2026-06-20T14:30:00Z',
  },
  {
    id: 3,
    customerId: 3,
    customerName: 'Global Logistics Inc',
    projectId: 3,
    projectTitle: 'Supply Chain Feasibility Study',
    rating: 5,
    title: 'Insightful analysis and recommendations',
    body: 'The feasibility study provided invaluable insights into our supply chain operations. The recommendations were practical and have already started showing results.',
    status: 'approved',
    createdAt: '2026-05-10T09:15:00Z',
  },
  {
    id: 4,
    customerId: 4,
    customerName: 'Municipal Water Authority',
    projectId: 4,
    projectTitle: 'Water Treatment Plant Design',
    rating: 3,
    title: 'Good work with some communication gaps',
    body: 'The design work was solid, but there were some communication gaps during the project. The final deliverable met our requirements, but the process could have been smoother.',
    status: 'pending',
    createdAt: '2026-08-01T16:45:00Z',
  },
])

const showReviewForm = ref(false)
const selectedReview = ref<Review | null>(null)
const selectedStatus = ref('ALL')

const filteredReviews = ref(reviews.value)

const filterReviews = () => {
  if (selectedStatus.value === 'ALL') {
    filteredReviews.value = reviews.value
  } else {
    filteredReviews.value = reviews.value.filter(r => r.status === selectedStatus.value)
  }
}

const openEditReview = (review: Review) => {
  selectedReview.value = review
  showReviewForm.value = true
}

const handleReviewSave = (review: Review) => {
  console.log('Review saved:', review)
  // TODO: Call API to save review
}

const handleReviewDelete = (id: number) => {
  console.log('Review deleted:', id)
  // TODO: Call API to delete review
}

const getStarDisplay = (rating: number) => {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

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

onMounted(() => {
  // TODO: Fetch reviews from API
  // useGetReviews().then(data => { reviews.value = data })
})
</script>

<template>
  <div>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Reviews & Ratings</h1>
        <p class="text-gray-600 mt-1">Manage customer reviews and ratings</p>
      </div>
      <Button @click="showReviewForm = true">
        + Submit Review
      </Button>
    </div>

    <!-- Filters -->
    <Card class="mb-6">
      <CardContent class="p-4">
        <div class="flex flex-col sm:flex-row gap-4">
          <select
            v-model="selectedStatus"
            @change="filterReviews"
            class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">All Status</option>
            <option value="pending">Pending</option>
            <option value="approved">Approved</option>
            <option value="rejected">Rejected</option>
          </select>
        </div>
      </CardContent>
    </Card>

    <!-- Reviews List -->
    <div class="grid gap-6">
      <Card
        v-for="review in filteredReviews"
        :key="review.id"
        class="cursor-pointer hover:shadow-md transition-shadow"
        @click="openEditReview(review)"
      >
        <CardHeader>
          <div class="flex items-center justify-between">
            <CardTitle class="text-lg">{{ review.title }}</CardTitle>
            <Badge :class="statusColors[review.status]">
              {{ statusLabels[review.status] }}
            </Badge>
          </div>
          <div class="flex items-center gap-2 text-sm text-muted-foreground">
            <span>{{ review.customerName }}</span>
            <span>•</span>
            <span>{{ review.projectTitle }}</span>
            <span>•</span>
            <span>{{ formatDate(review.createdAt) }}</span>
          </div>
        </CardHeader>
        <CardContent>
          <div class="flex items-center gap-2 mb-3">
            <span class="text-yellow-400 text-lg">{{ getStarDisplay(review.rating) }}</span>
            <span class="text-sm text-muted-foreground">({{ review.rating }}/5)</span>
          </div>
          <p class="text-gray-700">{{ review.body }}</p>
        </CardContent>
      </Card>
    </div>

    <div v-if="filteredReviews.length === 0" class="p-12 text-center">
      <p class="text-gray-600">No reviews found matching your criteria.</p>
    </div>

    <!-- Submit Review Form -->
    <SubmitReviewForm
      v-model:open="showReviewForm"
      :review="selectedReview"
      @save="handleReviewSave"
      @delete="handleReviewDelete"
    />
  </div>
</template>
