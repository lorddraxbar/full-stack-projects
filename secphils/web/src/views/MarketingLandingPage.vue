<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

const router = useRouter()

interface Review {
  id: number
  customerName: string
  projectTitle: string
  rating: number
  title: string
  body: string
}

const reviews = ref<Review[]>([
  {
    id: 1,
    customerName: 'ABC Manufacturing',
    projectTitle: 'Manufacturing Process Optimization',
    rating: 5,
    title: 'Exceptional service and results',
    body: 'The team delivered beyond our expectations. The process optimization reduced our manufacturing time by 30%.',
  },
  {
    id: 2,
    customerName: 'XYZ Energy Corp',
    projectTitle: 'Energy Sector Compliance Audit',
    rating: 4,
    title: 'Thorough and professional audit',
    body: 'The compliance audit was comprehensive and well-documented. Identified several areas for improvement.',
  },
  {
    id: 3,
    customerName: 'Global Logistics Inc',
    projectTitle: 'Supply Chain Feasibility Study',
    rating: 5,
    title: 'Insightful analysis and recommendations',
    body: 'The feasibility study provided invaluable insights into our supply chain operations.',
  },
])

const features = [
  {
    title: 'Project Management',
    description: 'Track all projects in one place with real-time progress updates, team collaboration, and milestone tracking.',
    icon: '📊',
  },
  {
    title: 'Document Management',
    description: 'Upload, version, and share project documents with your team. Full audit trail and access control.',
    icon: '📄',
  },
  {
    title: 'Task Assignment',
    description: 'Assign tasks, set deadlines, and track completion. Automatic notifications keep everyone on schedule.',
    icon: '✅',
  },
  {
    title: 'Team Collaboration',
    description: 'Dedicated project conversations with all stakeholders. Real-time messaging and announcements.',
    icon: '👥',
  },
  {
    title: 'Reviews & Ratings',
    description: 'Share and manage customer reviews. Build trust with transparent feedback from past projects.',
    icon: '⭐',
  },
  {
    title: 'Service Catalog',
    description: 'Browse and request additional services. From feasibility studies to compliance audits, we cover it all.',
    icon: '📋',
  },
]

const services = [
  'Feasibility Study',
  'Process Optimization',
  'Engineering Design',
  'Compliance Audit',
  'Energy Assessment',
  'Supply Chain Analysis',
  'Water Treatment Design',
  'Other Custom Services',
]

const getStarDisplay = (rating: number) => {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

onMounted(() => {
  // TODO: Fetch approved reviews from API
  // useGetApprovedReviews().then(data => { reviews.value = data })
})
</script>

<template>
  <div class="min-h-screen">
    <!-- Hero Section -->
    <section class="relative bg-gradient-to-br from-blue-900 via-blue-800 to-indigo-900 text-white">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
        <div class="text-center">
          <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold mb-6">
            Your Secure Client Portal
          </h1>
          <p class="text-xl sm:text-2xl text-blue-100 mb-8 max-w-3xl mx-auto">
            Streamline project management, collaboration, and delivery with our enterprise-grade client portal.
          </p>
          <div class="flex flex-col sm:flex-row gap-4 justify-center">
            <Button size="lg" @click="router.push('/login')">
              Get Started
            </Button>
            <Button variant="outline" size="lg" class="bg-white/10 text-white border-white/20 hover:bg-white/20" @click="router.push('/register')">
              Request a Demo
            </Button>
          </div>
        </div>
      </div>

      <!-- Decorative elements -->
      <div class="absolute inset-0 overflow-hidden">
        <div class="absolute -top-40 -right-40 w-80 h-80 bg-blue-500/20 rounded-full blur-3xl" />
        <div class="absolute -bottom-40 -left-40 w-80 h-80 bg-indigo-500/20 rounded-full blur-3xl" />
      </div>
    </section>

    <!-- Features Section -->
    <section class="py-20 bg-gray-50">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-16">
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
            Everything You Need
          </h2>
          <p class="text-xl text-gray-600 max-w-2xl mx-auto">
            Powerful tools to manage your projects, collaborate with your team, and track progress in real-time.
          </p>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          <Card
            v-for="feature in features"
            :key="feature.title"
            class="hover:shadow-lg transition-shadow"
          >
            <CardHeader>
              <div class="text-4xl mb-2">{{ feature.icon }}</div>
              <CardTitle>{{ feature.title }}</CardTitle>
            </CardHeader>
            <CardContent>
              <p class="text-gray-600">{{ feature.description }}</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>

    <!-- Services Section -->
    <section class="py-20 bg-white">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-16">
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
            Our Services
          </h2>
          <p class="text-xl text-gray-600 max-w-2xl mx-auto">
            Comprehensive consulting services tailored to your industry needs.
          </p>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          <Badge
            v-for="service in services"
            :key="service"
            variant="secondary"
            class="text-lg px-6 py-3 justify-center cursor-pointer hover:bg-primary hover:text-primary-foreground transition-colors"
          >
            {{ service }}
          </Badge>
        </div>
      </div>
    </section>

    <!-- Reviews Section -->
    <section class="py-20 bg-gray-50">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-16">
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
            What Our Clients Say
          </h2>
          <p class="text-xl text-gray-600 max-w-2xl mx-auto">
            Trusted by leading companies across industries.
          </p>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          <Card
            v-for="review in reviews"
            :key="review.id"
            class="hover:shadow-lg transition-shadow"
          >
            <CardHeader>
              <div class="flex items-center gap-2 mb-2">
                <span class="text-yellow-400 text-lg">{{ getStarDisplay(review.rating) }}</span>
              </div>
              <CardTitle class="text-lg">{{ review.title }}</CardTitle>
              <p class="text-sm text-muted-foreground">
                {{ review.customerName }} — {{ review.projectTitle }}
              </p>
            </CardHeader>
            <CardContent>
              <p class="text-gray-700">{{ review.body }}</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="py-20 bg-gradient-to-r from-blue-600 to-indigo-600 text-white">
      <div class="max-w-4xl mx-auto text-center px-4 sm:px-6 lg:px-8">
        <h2 class="text-3xl sm:text-4xl font-bold mb-6">
          Ready to Get Started?
        </h2>
        <p class="text-xl text-blue-100 mb-8">
          Join hundreds of companies using our portal to streamline their project delivery.
        </p>
        <Button size="lg" variant="secondary" @click="router.push('/login')">
          Create Your Account
        </Button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="bg-gray-900 text-gray-400 py-12">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-8">
          <div>
            <h3 class="text-white font-semibold mb-4">SECPhils</h3>
            <p class="text-sm">Your secure client portal for project management and collaboration.</p>
          </div>
          <div>
            <h4 class="text-white font-semibold mb-4">Services</h4>
            <ul class="space-y-2 text-sm">
              <li>Feasibility Study</li>
              <li>Process Optimization</li>
              <li>Engineering Design</li>
              <li>Compliance Audit</li>
            </ul>
          </div>
          <div>
            <h4 class="text-white font-semibold mb-4">Company</h4>
            <ul class="space-y-2 text-sm">
              <li>About Us</li>
              <li>Contact</li>
              <li>Careers</li>
              <li>Blog</li>
            </ul>
          </div>
          <div>
            <h4 class="text-white font-semibold mb-4">Legal</h4>
            <ul class="space-y-2 text-sm">
              <li>Privacy Policy</li>
              <li>Terms of Service</li>
              <li>Cookie Policy</li>
            </ul>
          </div>
        </div>
        <div class="border-t border-gray-800 mt-8 pt-8 text-center text-sm">
          <p>&copy; 2026 SECPhils. All rights reserved.</p>
        </div>
      </div>
    </footer>
  </div>
</template>
