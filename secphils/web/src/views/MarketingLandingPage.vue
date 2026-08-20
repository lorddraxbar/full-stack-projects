<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { useGetLanding, type LandingCompany, type LandingReview } from '@/services/api'

const router = useRouter()

// ---------- Company Profile (from Admin Panel > Company Settings) ----------
const company = ref<LandingCompany | null>(null)
const loading = ref(true)

// Fallbacks mirror www.secphils.com until the profile fields are filled in
const fallback = {
  name: 'Strategic Engineering Consultancy',
  tagline: 'Helping Your Business Succeed Is Our Top Priority',
  description:
    'We are a Filipino company, owned and operated by highly competitive engineers. Each professional on our team has multiple years of industry experience with very high attention to quality.',
  headquarters: '60F Tower, 2000 Shaw Boulevard, Makati City',
  phone: '+63 2 8888 1234',
  email: 'manager@secphils.com',
  website: 'https://www.secphils.com',
  industrySectors: 'Manufacturing, Energy, Finance, Government',
  brandPrimary: '#2563eb',
  brandSecondary: '#64748b',
}

const c = computed(() => ({
  name: company.value?.name?.trim() || fallback.name,
  tagline: company.value?.tagline?.trim() || fallback.tagline,
  description: company.value?.description?.trim() || fallback.description,
  headquarters: company.value?.headquarters?.trim() || fallback.headquarters,
  phone: company.value?.phone?.trim() || fallback.phone,
  email: company.value?.email?.trim() || fallback.email,
  website: company.value?.website?.trim() || fallback.website,
  sectors: (company.value?.industrySectors?.trim() || fallback.industrySectors)
    .split(',').map(s => s.trim()).filter(Boolean),
  brandPrimary: company.value?.brandPrimary || fallback.brandPrimary,
  brandSecondary: company.value?.brandSecondary || fallback.brandSecondary,
}))

const heroStyle = computed(() => ({
  background: `linear-gradient(135deg, ${c.value.brandPrimary} 0%, ${c.value.brandSecondary} 100%)`,
}))

// ---------- Services (ported from www.secphils.com) ----------
interface ServiceTab {
  key: string
  label: string
  title: string
  body?: string
  body2?: string
  list?: string[]
}
const serviceTabs: ServiceTab[] = [
  {
    key: 'ecc',
    label: 'ECC',
    title: 'Environmental Compliance Certificate (ECC)',
    body: 'The Environmental Compliance Certificate or ECC refers to the document issued by the DENR/Environmental Management Board (EMB) that allows the project to proceed to the next stage of project planning, which is the acquisition of approvals from other government agencies and LGUs, after which the project can start implementation.',
    body2: 'It certifies that the proponent has complied with the requirements of the Environmental Impact Statement (EIS) system and that the proposed project will not cause a significant negative impact on the environment. It also certifies that the proponent is committed to implement its approved Environment Management Plan. Requirements for ECC application depend on the type and location of project being developed.',
  },
  {
    key: 'cnc',
    label: 'CNC',
    title: 'Certificate of Non-Coverage (CNC)',
    body: 'The Certificate of Non-Coverage is a document issued by the DENR/Environmental Management Board (EMB) certifying that, based on the submitted project description, the project is not covered by the EIS (Environmental Impact Statement) system and is not required to secure an ECC. This covers projects which are not critical to the environment.',
  },
  {
    key: 'other',
    label: 'Other Services',
    title: 'Other Services We Provide',
    list: [
      'Environmental Impact Assessment (EIA)',
      'Discharge Permit (DP)',
      'Permit for Operation of Air Pollutant Sources and Central Installation',
      'Hazardous Waste Generator ID',
      'Feasibility Studies for Businesses',
    ],
  },
]
const activeTab = ref<ServiceTab>(serviceTabs[0])

// ---------- Reviews (approved only, from the API) ----------
const reviews = ref<LandingReview[]>([])
const getStarDisplay = (rating: number) => '★'.repeat(rating) + '☆'.repeat(5 - rating)

const scrollTo = (id: string) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

onMounted(async () => {
  try {
    const data = await useGetLanding()
    if (data.company && Object.keys(data.company).length > 0) company.value = data.company
    reviews.value = data.reviews || []
  } catch {
    // Public page degrades gracefully to fallback copy on API failure
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="min-h-screen bg-white">
    <!-- Top contact bar (profile phone / email) -->
    <div class="text-white text-sm" :style="{ backgroundColor: c.brandSecondary }">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-2 flex justify-end gap-6">
        <a :href="`mailto:${c.email}`" class="hover:underline">{{ c.email }}</a>
        <a :href="`tel:${c.phone.replace(/\s/g, '')}`" class="hover:underline">{{ c.phone }}</a>
      </div>
    </div>

    <!-- Nav -->
    <header class="sticky top-0 z-40 bg-white shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <a href="#" class="flex items-center gap-2" @click.prevent="scrollTo('top')">
            <span
              class="flex h-9 w-9 items-center justify-center rounded-md text-white font-bold"
              :style="{ backgroundColor: c.brandPrimary }"
            >
              {{ c.name.charAt(0) }}
            </span>
            <span class="font-semibold text-gray-900 leading-tight">
              {{ c.name }}
            </span>
          </a>
          <nav class="hidden md:flex items-center gap-8 text-sm font-medium text-gray-600">
            <a href="#" class="hover:text-gray-900" @click.prevent="scrollTo('top')">Home</a>
            <a href="#" class="hover:text-gray-900" @click.prevent="scrollTo('services')">Services</a>
            <a href="#" class="hover:text-gray-900" @click.prevent="scrollTo('about')">About</a>
            <a href="#" class="hover:text-gray-900" @click.prevent="scrollTo('contact')">Contact</a>
          </nav>
          <Button @click="scrollTo('contact')">Get Started</Button>
        </div>
      </div>
    </header>

    <!-- Hero -->
    <section id="top" class="relative text-white" :style="heroStyle">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
        <div class="text-center">
          <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold mb-6">
            Highest level of service you can rely on
          </h1>
          <p class="text-xl sm:text-2xl text-white/90 mb-10 max-w-3xl mx-auto">
            {{ c.tagline }}
          </p>
          <div class="flex flex-col sm:flex-row gap-4 justify-center">
            <Button size="lg" variant="secondary" @click="scrollTo('contact')">Get Started</Button>
            <Button
              size="lg"
              class="bg-white/10 border border-white/40 text-white hover:bg-white/20"
              @click="scrollTo('services')"
            >
              Our Services
            </Button>
          </div>
        </div>
      </div>
      <div class="absolute inset-0 overflow-hidden pointer-events-none">
        <div class="absolute -top-40 -right-40 w-80 h-80 bg-white/10 rounded-full blur-3xl" />
        <div class="absolute -bottom-40 -left-40 w-80 h-80 bg-white/10 rounded-full blur-3xl" />
      </div>
    </section>

    <!-- Services -->
    <section id="services" class="py-20 bg-gray-50 scroll-mt-16">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-12">
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">Our Services</h2>
          <p class="text-xl text-gray-600 max-w-2xl mx-auto">
            Environmental permits, compliance, and studies — handled end to end.
          </p>
        </div>

        <div class="flex justify-center gap-2 mb-8 flex-wrap">
          <button
            v-for="tab in serviceTabs"
            :key="tab.key"
            @click="activeTab = tab"
            :class="[
              'px-6 py-2.5 rounded-full text-sm font-medium transition-colors',
              activeTab.key === tab.key
                ? 'text-white'
                : 'bg-white text-gray-700 border border-gray-200 hover:border-gray-300'
            ]"
            :style="activeTab.key === tab.key ? { backgroundColor: c.brandPrimary } : {}"
          >
            {{ tab.label }}
          </button>
        </div>

        <Card class="max-w-3xl mx-auto">
          <CardHeader>
            <CardTitle class="text-xl">{{ activeTab.title }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <p v-if="activeTab.body" class="text-gray-600 leading-relaxed">{{ activeTab.body }}</p>
            <p v-if="activeTab.body2" class="text-gray-600 leading-relaxed">{{ activeTab.body2 }}</p>
            <ul v-if="activeTab.list" class="space-y-2">
              <li v-for="item in activeTab.list" :key="item" class="flex items-start gap-3 text-gray-600">
                <span class="mt-1 h-1.5 w-1.5 rounded-full shrink-0" :style="{ backgroundColor: c.brandPrimary }" />
                {{ item }}
              </li>
            </ul>
            <div class="pt-2">
              <Button @click="scrollTo('contact')">Get Started</Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </section>

    <!-- About (populated from Admin Panel > Company Settings > Company Profile) -->
    <section id="about" class="py-20 bg-white scroll-mt-16">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-12">
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">About</h2>
        </div>
        <div class="max-w-3xl mx-auto text-center">
          <h3 class="text-2xl font-semibold text-gray-900 mb-4">{{ c.name }}</h3>
          <p class="text-lg text-gray-600 leading-relaxed mb-8">{{ c.description }}</p>

          <div v-if="c.sectors.length" class="flex flex-wrap justify-center gap-2 mb-10">
            <Badge v-for="sector in c.sectors" :key="sector" variant="secondary">{{ sector }}</Badge>
          </div>

          <div class="rounded-lg border border-gray-200 bg-gray-50 px-8 py-6 text-left">
            <h4 class="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-4">Visit us</h4>
            <ul class="space-y-2 text-gray-700 text-sm">
              <li class="flex items-start gap-2">
                <i class="fas fa-location-dot mt-0.5 text-gray-400" /> {{ c.headquarters }}
              </li>
              <li class="flex items-start gap-2">
                <i class="fas fa-envelope mt-0.5 text-gray-400" />
                <a :href="`mailto:${c.email}`" class="hover:underline">{{ c.email }}</a>
              </li>
              <li class="flex items-start gap-2">
                <i class="fas fa-phone mt-0.5 text-gray-400" />
                <a :href="`tel:${c.phone.replace(/\s/g, '')}`" class="hover:underline">{{ c.phone }}</a>
              </li>
              <li v-if="c.website" class="flex items-start gap-2">
                <i class="fas fa-globe mt-0.5 text-gray-400" />
                <a :href="c.website" target="_blank" rel="noopener" class="hover:underline">{{ c.website }}</a>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Reviews -->
    <section id="reviews" class="py-20 bg-gray-50 scroll-mt-16">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-16">
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">What Our Clients Say</h2>
          <p class="text-xl text-gray-600 max-w-2xl mx-auto">
            Trusted by leading companies across industries.
          </p>
        </div>

        <div v-if="!loading && reviews.length" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
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
                {{ review.customerName || 'Client' }}
                <template v-if="review.projectName"> — {{ review.projectName }}</template>
              </p>
            </CardHeader>
            <CardContent>
              <p class="text-gray-700">{{ review.body }}</p>
            </CardContent>
          </Card>
        </div>

        <div v-else class="max-w-xl mx-auto text-center rounded-lg border border-dashed border-gray-300 bg-white px-8 py-12">
          <i class="fas fa-star text-3xl text-gray-300 mb-4" />
          <p class="text-gray-500">
            Client reviews will appear here once they are approved in the portal.
          </p>
        </div>
      </div>
    </section>

    <!-- Contact -->
    <section id="contact" class="py-20 bg-white scroll-mt-16">
      <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">Say hello to us</h2>
        <p class="text-xl text-gray-600 mb-10">
          Tell us about your project and we'll get back to you within one business day.
        </p>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-10">
          <a :href="`mailto:${c.email}`" class="rounded-lg border border-gray-200 p-6 hover:shadow-md transition-shadow">
            <i class="fas fa-envelope text-2xl mb-3" :style="{ color: c.brandPrimary }" />
            <p class="text-sm font-medium text-gray-900 mb-1">Email</p>
            <p class="text-sm text-gray-600 break-all">{{ c.email }}</p>
          </a>
          <a :href="`tel:${c.phone.replace(/\s/g, '')}`" class="rounded-lg border border-gray-200 p-6 hover:shadow-md transition-shadow">
            <i class="fas fa-phone text-2xl mb-3" :style="{ color: c.brandPrimary }" />
            <p class="text-sm font-medium text-gray-900 mb-1">Phone</p>
            <p class="text-sm text-gray-600">{{ c.phone }}</p>
          </a>
          <div class="rounded-lg border border-gray-200 p-6 hover:shadow-md transition-shadow">
            <i class="fas fa-location-dot text-2xl mb-3" :style="{ color: c.brandPrimary }" />
            <p class="text-sm font-medium text-gray-900 mb-1">Office</p>
            <p class="text-sm text-gray-600">{{ c.headquarters }}</p>
          </div>
        </div>
        <Button size="lg" @click="router.push('/auth/login')">Portal Login</Button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="bg-gray-900 text-gray-400 py-12">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-8">
          <div>
            <h3 class="text-white font-semibold mb-4">{{ c.name }}</h3>
            <p class="text-sm">{{ c.tagline }}</p>
          </div>
          <div>
            <h4 class="text-white font-semibold mb-4">Services</h4>
            <ul class="space-y-2 text-sm">
              <li>ECC</li>
              <li>CNC</li>
              <li>Environmental Impact Assessment</li>
              <li>Feasibility Studies</li>
            </ul>
          </div>
          <div>
            <h4 class="text-white font-semibold mb-4">Company</h4>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white" @click.prevent="scrollTo('about')">About Us</a></li>
              <li><a href="#" class="hover:text-white" @click.prevent="scrollTo('reviews')">Reviews</a></li>
              <li><a href="#" class="hover:text-white" @click.prevent="scrollTo('contact')">Contact</a></li>
              <li><a href="/auth/login" class="hover:text-white">Portal Login</a></li>
            </ul>
          </div>
          <div>
            <h4 class="text-white font-semibold mb-4">Contact</h4>
            <ul class="space-y-2 text-sm">
              <li>{{ c.headquarters }}</li>
              <li>{{ c.phone }}</li>
              <li class="break-all">{{ c.email }}</li>
            </ul>
          </div>
        </div>
        <div class="border-t border-gray-800 mt-8 pt-8 text-center text-sm">
          <p>&copy; {{ new Date().getFullYear() }} {{ c.name }}. All rights reserved.</p>
        </div>
      </div>
    </footer>
  </div>
</template>
