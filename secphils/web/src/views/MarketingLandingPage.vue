<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { useGetLanding, usePostLandingContact, type LandingCompany, type LandingReview } from '@/services/api'

const router = useRouter()

// ---------- Company profile (Admin Panel > Company Settings) ----------
const company = ref<LandingCompany | null>(null)

// Fallbacks mirror www.secphils.com
const fallback = {
  name: 'Strategic Engineering Consultancy',
  phone: '+63 2 8888 1234',
  email: 'manager@secphils.com',
  facebook: 'https://www.facebook.com/strategicengineeringconsultancy/',
  description:
    'We are a Filipino company, owned and operated by highly competitive engineers. Each professional on our team has multiple years of industry experience with very high attention to quality.',
}

const splitList = (raw: string | undefined | null): string[] =>
  (raw ?? '')
    .split(/[,;]+/)
    .map((s) => s.trim())
    .filter(Boolean)

const emails = computed<string[]>(() => {
  const list = splitList(company.value?.email)
  return list.length ? list : [fallback.email]
})
const phones = computed<string[]>(() => {
  const list = splitList(company.value?.phone)
  return list.length ? list : [fallback.phone]
})
const primaryEmail = computed(() => emails.value[0] ?? fallback.email)
const primaryPhone = computed(() => phones.value[0] ?? fallback.phone)

interface SocialLink {
  label: string
  url: string
  icon: string
}
const socialLinks = computed<SocialLink[]>(() => {
  const raw = company.value?.socialLinks?.trim() || fallback.facebook
  const items = splitList(raw)
  return items.map((url) => {
    let host = ''
    try {
      host = new URL(url).hostname.toLowerCase()
    } catch {
      host = url.toLowerCase()
    }
    let icon = 'fa-solid fa-globe'
    let label = 'Website'
    if (host.includes('facebook.com')) {
      icon = 'fa-brands fa-facebook-f'
      label = 'Facebook'
    } else if (host.includes('linkedin.com')) {
      icon = 'fa-brands fa-linkedin-in'
      label = 'LinkedIn'
    } else if (host === 'x.com' || host.includes('twitter.com')) {
      icon = 'fa-brands fa-x-twitter'
      label = 'X (Twitter)'
    } else if (host.includes('instagram.com')) {
      icon = 'fa-brands fa-instagram'
      label = 'Instagram'
    } else if (host.includes('youtube.com')) {
      icon = 'fa-brands fa-youtube'
      label = 'YouTube'
    } else {
      try {
        label = new URL(url).hostname.replace(/^www\./, '')
      } catch {
        label = 'Website'
      }
    }
    return { label, url, icon }
  })
})

const c = computed(() => ({
  name: company.value?.name?.trim() || fallback.name,
  description: company.value?.description?.trim() || fallback.description,
}))

const year = new Date().getFullYear()

// ---------- Page title (Admin Panel > Company Settings drives the profile, title is fixed per brand) ----------
watch(
  () => router.currentRoute.value.fullPath,
  () => {
    document.title = 'Strategic Engineering Consultancy - Philippines'
  },
)

// ---------- Navbar ----------
const scrolled = ref(false)
const menuOpen = ref(false)
const onScroll = () => {
  scrolled.value = window.scrollY > 60
}

// Reviews come second-to-last, before "Say hello to us"; hidden entirely when 0 approved reviews
const hasReviews = computed(() => reviews.value.length > 0)
const navLinks = computed(() => {
  const links = [
    { id: 'home', label: 'Home' },
    { id: 'services', label: 'Services' },
  ]
  if (hasReviews.value) links.push({ id: 'reviews', label: 'Reviews' })
  links.push({ id: 'about', label: 'About' })
  return links
})

const scrollTo = (id: string) => {
  menuOpen.value = false
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

// ---------- Services (content ported from www.secphils.com) ----------
interface ServiceTab {
  key: string
  label: string
  icon: string
  title: string
  short: string
  paras?: string[]
  list?: string[]
}
const serviceTabs: ServiceTab[] = [
  {
    key: 'ecc',
    label: 'ECC',
    icon: 'fa-solid fa-leaf',
    title: 'Environmental Compliance Certificate (ECC)',
    short:
      'The DENR/EMB clearance that lets your project move on to the next stage of planning and implementation.',
    paras: [
      'The Environmental Compliance Certificate or ECC refers to the document issued by the DENR/Environmental Management Board (EMB) that allows the project to proceed to the next stage of project planning, which is the acquisition of approvals from other government agencies and LGUs, after which the project can start implementation.',
      'It certifies that the proponent has complied with the requirements of the Environmental Impact Statement (EIS) system and that the proposed project will not cause a significant negative impact on the environment. It also certifies that the proponent is committed to implement its approved Environment Management Plan. Requirements for ECC application depend on the type and location of project being developed.',
    ],
  },
  {
    key: 'cnc',
    label: 'CNC',
    icon: 'fa-solid fa-circle-check',
    title: 'Certificate of Non-Coverage (CNC)',
    short:
      'Proof that your project is not covered by the EIS system and is not required to secure an ECC.',
    paras: [
      'The Certificate of Non-Coverage is a document issued by the DENR/Environmental Management Board (EMB) certifying that, based on the submitted project description, the project is not covered by the EIS (Environmental Impact Statement) system and is not required to secure an ECC. This covers projects which are not critical to the environment.',
    ],
  },
  {
    key: 'other',
    label: 'Other Services',
    icon: 'fa-solid fa-toolbox',
    title: 'Other Services',
    short:
      'A wider portfolio of environmental and business compliance engagements, scoped to what your project needs.',
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
const loading = ref(true)
const stars = (rating: number) => '★'.repeat(Math.max(0, Math.min(5, rating))) + '☆'.repeat(5 - Math.max(0, Math.min(5, rating)))

// ---------- Contact form (Get Started / email card) ----------
const contactOpen = ref(false)
const sending = ref(false)
const sendError = ref('')
const sent = ref(false)
const sentTo = ref('')
const form = ref({
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  message: '',
})
const fieldErrors = ref<Record<string, string>>({})

const openContact = () => {
  sendError.value = ''
  sent.value = false
  fieldErrors.value = {}
  contactOpen.value = true
}
const resetContact = () => {
  form.value = { firstName: '', lastName: '', email: '', phone: '', message: '' }
  fieldErrors.value = {}
}
const validateContact = (): boolean => {
  const e: Record<string, string> = {}
  if (!form.value.firstName.trim()) e.firstName = 'First name is required.'
  if (!form.value.lastName.trim()) e.lastName = 'Last name is required.'
  if (!form.value.email.trim()) e.email = 'Email is required.'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email.trim())) e.email = 'Enter a valid email address.'
  if (!form.value.phone.trim()) e.phone = 'Phone number is required.'
  if (!form.value.message.trim()) e.message = 'Tell us how we can help.'
  fieldErrors.value = e
  return Object.keys(e).length === 0
}
const submitContact = async () => {
  if (sending.value || !validateContact()) return
  sending.value = true
  sendError.value = ''
  try {
    await usePostLandingContact({
      firstName: form.value.firstName.trim(),
      lastName: form.value.lastName.trim(),
      email: form.value.email.trim(),
      phone: form.value.phone.trim(),
      message: form.value.message.trim(),
    })
    sentTo.value = form.value.email.trim()
    sent.value = true
    resetContact()
  } catch {
    sendError.value = 'We could not send your message. Please try again or email us directly.'
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  document.title = 'Strategic Engineering Consultancy - Philippines'
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  useGetLanding()
    .then((data) => {
      if (data.company && Object.keys(data.company).length > 0) company.value = data.company
      reviews.value = data.reviews || []
    })
    .catch(() => {
      // Public page degrades gracefully to fallback copy on API failure
    })
    .finally(() => {
      loading.value = false
    })
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="min-h-screen" style="background: #f9f9f9; font-family: 'Open Sans', ui-sans-serif, system-ui, sans-serif">
    <!-- ================= NAVBAR ================= -->
    <header
      class="fixed inset-x-0 top-0 z-50 transition-all duration-300"
      :class="scrolled ? 'bg-white py-2.5 shadow-[0_1px_30px_rgba(0,0,0,0.1)]' : 'bg-transparent py-6'"
    >
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="flex items-center justify-between">
          <a href="#home" class="flex items-center gap-3" @click.prevent="scrollTo('home')">
            <img src="/images/landing/seclogo.png" alt="Strategic Engineering Consultancy" class="h-[52px] w-auto" />
            <span
              class="hidden text-lg font-medium tracking-tight lg:block"
              :class="scrolled ? 'text-[#252525]' : 'text-white'"
            >
              {{ c.name }}
            </span>
          </a>

          <!-- Desktop links -->
          <nav class="hidden items-center lg:flex">
            <a
              v-for="l in navLinks"
              :key="l.id"
              href="#"
              class="px-5 py-2 text-[15px] transition-colors"
              :class="scrolled ? 'text-[#575757] hover:text-[#29ca8e]' : 'text-[#f0f0f0] hover:text-[#29ca8e]'"
              @click.prevent="scrollTo(l.id)"
            >
              {{ l.label }}
            </a>
            <button
              class="ml-4 rounded-full border-2 px-5 py-2 text-sm font-semibold transition-colors"
              :class="
                scrolled
                  ? 'border-[#29ca8e] text-[#29ca8e] hover:bg-[#29ca8e] hover:text-white'
                  : 'border-white text-white hover:bg-white hover:text-[#252525]'
              "
              @click="openContact"
            >
              Get Started
            </button>
          </nav>

          <!-- Mobile hamburger -->
          <button
            class="p-2 lg:hidden"
            :class="scrolled || menuOpen ? 'text-[#252525]' : 'text-white'"
            aria-label="Toggle menu"
            @click="menuOpen = !menuOpen"
          >
            <i class="fas fa-bars text-2xl"></i>
          </button>
        </div>
      </div>

      <!-- Mobile menu -->
      <div v-if="menuOpen" class="border-t border-gray-100 bg-white shadow-lg lg:hidden">
        <a
          v-for="l in navLinks"
          :key="l.id"
          href="#"
          class="block px-6 py-3 text-[#575757] hover:text-[#29ca8e]"
          @click.prevent="scrollTo(l.id)"
        >
          {{ l.label }}
        </a>
        <div class="px-6 py-3">
          <button
            class="w-full rounded-full border-2 border-[#29ca8e] px-5 py-2 text-sm font-semibold text-[#29ca8e]"
            @click="openContact"
          >
            Get Started
          </button>
        </div>
      </div>
    </header>

    <!-- ================= HERO ================= -->
    <section id="home" class="relative flex min-h-screen items-center justify-center text-center">
      <img src="/images/landing/home-bg.jpg" alt="" class="absolute inset-0 h-full w-full object-cover" />
      <div class="absolute inset-0" style="background: linear-gradient(to right, #292E49, #536976); opacity: 0.9" />
      <div class="relative z-10 mx-auto max-w-3xl px-4 pb-16 pt-28">
        <h3 class="my-2.5 text-[11px] font-bold uppercase tracking-[4px] text-[#f0f0f0]">
          Highest level of service you can rely on
        </h3>
        <h1 class="mt-2.5 mb-10 font-light leading-tight text-white text-3xl sm:text-4xl md:text-5xl">
          Helping Your Business Succeed Is Our Top Priority
        </h1>
        <button class="hero-btn" @click="openContact">Get Started</button>
      </div>
    </section>

    <!-- ================= SERVICES (modernized) ================= -->
    <section id="services" class="scroll-mt-0 py-20" style="background: #f9f9f9">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10">
          <p class="section-eyebrow">What we do</p>
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">Our Services</h1>
          <p class="mt-3 max-w-2xl text-sm leading-6" style="color: #757575">
            Environmental compliance and business engineering services, handled end to end by our
            team of licensed Filipino engineers.
          </p>
        </div>

        <div class="flex flex-col gap-10 lg:flex-row">
          <!-- Tabs + content -->
          <div class="lg:w-[58%]">
            <div class="grid grid-cols-3 gap-3">
              <button
                v-for="tab in serviceTabs"
                :key="tab.key"
                class="svc-tab"
                :class="activeTab.key === tab.key ? 'is-active' : ''"
                @click="activeTab = tab"
              >
                <i :class="tab.icon" class="mb-2 block text-lg"></i>
                <span class="text-sm font-semibold">{{ tab.label }}</span>
              </button>
            </div>

            <div class="mt-6 min-h-[300px] rounded-2xl border p-8 sm:p-10" style="border-color: #e8e8e8; background: #ffffff">
              <h2 class="mb-1.5 font-light text-2xl sm:text-3xl" style="color: #353535">{{ activeTab.title }}</h2>
              <p class="mb-4 text-base leading-6" style="color: #29ca8e; font-weight: 600">{{ activeTab.short }}</p>
              <template v-if="activeTab.paras">
                <p
                  v-for="(p, i) in activeTab.paras"
                  :key="i"
                  class="mb-2.5 text-sm leading-6"
                  style="color: #757575"
                >
                  {{ p }}
                </p>
              </template>
              <ul v-if="activeTab.list" class="space-y-3 pt-1">
                <li
                  v-for="item in activeTab.list"
                  :key="item"
                  class="flex items-start gap-3 font-light text-lg sm:text-xl"
                  style="color: #353535"
                >
                  <span class="svc-check">
                    <i class="fa-solid fa-check"></i>
                  </span>
                  {{ item }}
                </li>
              </ul>
              <div class="mt-8">
                <button class="hero-btn-sm" @click="openContact">Get Started</button>
              </div>
            </div>
          </div>

          <!-- Logo image -->
          <div class="flex items-center justify-center lg:w-[42%]">
            <img src="/images/landing/seclogo.png" alt="Services" class="w-auto max-h-[420px]" />
          </div>
        </div>
      </div>
    </section>

    <!-- ================= REVIEWS (conditional — hidden with 0 approved reviews) ================= -->
    <section v-if="hasReviews" id="reviews" class="py-20 scroll-mt-16" style="background: #ffffff">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10 text-center">
          <p class="section-eyebrow">Client feedback</p>
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">What People Say</h1>
        </div>

        <div v-if="!loading" class="grid gap-6 md:grid-cols-3">
          <div
            v-for="r in reviews"
            :key="r.id"
            class="rounded-2xl border p-8 transition-shadow hover:shadow-md"
            style="border-color: #e8e8e8; background: #f9f9f9"
          >
            <div class="mb-3 text-lg" style="color: #f5a623">{{ stars(r.rating) }}</div>
            <h3 class="mb-4 font-light text-xl leading-snug" style="color: #353535">{{ r.title }}</h3>
            <p class="text-sm leading-6" style="color: #757575">{{ r.body }}</p>
            <p class="mt-4 text-sm font-semibold" style="color: #202020">
              {{ r.customerName || 'Client' }}
              <span v-if="r.projectName" class="font-normal" style="color: #757575"> — {{ r.projectName }}</span>
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- ================= ABOUT (modernized) ================= -->
    <section id="about" class="py-20 scroll-mt-16" style="background: #f9f9f9">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10">
          <p class="section-eyebrow">Who we are</p>
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">About</h1>
        </div>

        <div class="grid items-stretch gap-10 lg:grid-cols-2">
          <!-- Left: description + socials -->
          <div class="flex flex-col rounded-2xl border bg-white p-8 sm:p-10" style="border-color: #e8e8e8">
            <i class="fa-solid fa-building-columns mb-4 block text-2xl" style="color: #29ca8e"></i>
            <h2 class="mb-3 font-light text-2xl sm:text-3xl" style="color: #353535">{{ c.name }}</h2>
            <p class="mb-8 text-base leading-7" style="color: #757575">{{ c.description }}</p>

            <div class="mt-auto border-t pt-6" style="border-color: #ececec">
              <p class="mb-3 text-xs font-bold uppercase tracking-[2px]" style="color: #999999">Follow us</p>
              <div class="flex flex-wrap gap-3">
                <a
                  v-for="s in socialLinks"
                  :key="s.url"
                  :href="s.url"
                  target="_blank"
                  rel="noopener"
                  class="social-chip"
                  :title="s.label"
                >
                  <i :class="s.icon"></i>
                  <span>{{ s.label }}</span>
                </a>
              </div>
            </div>
          </div>

          <!-- Right: team photo collage -->
          <div class="grid grid-cols-2 gap-4">
            <img
              src="/images/landing/team-image1.jpg"
              alt="Our team at work"
              class="rounded-2xl object-cover shadow-sm lg:col-span-2 lg:aspect-[21/9]"
            />
            <div class="rounded-2xl bg-white p-6 text-center shadow-sm">
              <h3 class="mb-1.5 text-lg font-light" style="color: #353535">Visit us on</h3>
              <div class="mb-3 flex justify-center gap-3">
                <a
                  v-for="s in socialLinks"
                  :key="s.url"
                  :href="s.url"
                  target="_blank"
                  rel="noopener"
                  class="social-dot"
                  :title="s.label"
                >
                  <i :class="s.icon"></i>
                </a>
              </div>
              <a
                :href="`mailto:${primaryEmail}`"
                class="block truncate text-sm font-semibold"
                style="color: #202020"
              >
                {{ primaryEmail }}
              </a>
              <a
                :href="`tel:${primaryPhone.replace(/\s/g, '')}`"
                class="block text-sm"
                style="color: #757575"
              >
                {{ primaryPhone }}
              </a>
            </div>
            <img
              src="/images/landing/team-image2.jpg"
              alt="On site"
              class="rounded-2xl object-cover shadow-sm lg:aspect-auto"
            />
          </div>
        </div>

        <!-- Team strip -->
        <div class="mt-10 grid gap-4 sm:grid-cols-3">
          <div class="flex items-center gap-4 rounded-2xl bg-white p-5" style="border: 1px solid #e8e8e8">
            <span class="about-badge">
              <i class="fa-solid fa-users-gear"></i>
            </span>
            <div>
              <p class="text-sm font-semibold" style="color: #202020">Engineer-led team</p>
              <p class="text-xs leading-5" style="color: #757575">Owned and operated by licensed Filipino engineers.</p>
            </div>
          </div>
          <div class="flex items-center gap-4 rounded-2xl bg-white p-5" style="border: 1px solid #e8e8e8">
            <span class="about-badge">
              <i class="fa-solid fa-award"></i>
            </span>
            <div>
              <p class="text-sm font-semibold" style="color: #202020">Years of experience</p>
              <p class="text-xs leading-5" style="color: #757575">Every professional brings multiple years of industry practice.</p>
            </div>
          </div>
          <div class="flex items-center gap-4 rounded-2xl bg-white p-5" style="border: 1px solid #e8e8e8">
            <span class="about-badge">
              <i class="fa-solid fa-magnifying-glass-chart"></i>
            </span>
            <div>
              <p class="text-sm font-semibold" style="color: #202020">High attention to quality</p>
              <p class="text-xs leading-5" style="color: #757575">Compliance done thoroughly, from scoping to approval.</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ================= CONTACT ================= -->
    <section id="contact" class="py-20 scroll-mt-16 text-center" style="background: #ffffff">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10">
          <p class="section-eyebrow">Get in touch</p>
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">Say hello to us</h1>
        </div>

        <div class="mx-auto grid max-w-3xl gap-6 sm:grid-cols-3">
          <button
            type="button"
            class="contact-card"
            @click="openContact"
            title="Open the contact form"
          >
            <i class="fas fa-envelope mb-3 text-2xl" style="color: #29ca8e" />
            <p class="mb-1 text-sm font-semibold" style="color: #202020">Email</p>
            <p class="break-all text-sm" style="color: #757575">{{ primaryEmail }}</p>
            <p class="mt-2 text-xs font-semibold" style="color: #29ca8e">Send us a message →</p>
          </button>
          <a :href="`tel:${primaryPhone.replace(/\s/g, '')}`" class="contact-card">
            <i class="fas fa-phone mb-3 text-2xl" style="color: #29ca8e" />
            <p class="mb-1 text-sm font-semibold" style="color: #202020">Phone</p>
            <p v-for="p in phones" :key="p" class="text-sm" style="color: #757575">{{ p }}</p>
          </a>
          <a
            v-for="s in socialLinks.slice(0, 1)"
            :key="s.url"
            :href="s.url"
            target="_blank"
            rel="noopener"
            class="contact-card"
          >
            <i :class="s.icon" class="mb-3 text-2xl" style="color: #29ca8e" />
            <p class="mb-1 text-sm font-semibold" style="color: #202020">{{ s.label }}</p>
            <p class="text-sm leading-snug" style="color: #757575">
              {{ c.name || 'See our page' }}
            </p>
          </a>
        </div>

        <div class="mt-10">
          <button class="section-btn" @click="router.push('/auth/login')">Portal Login</button>
        </div>
      </div>
    </section>

    <!-- ================= FOOTER ================= -->
    <footer class="py-10 text-center" style="background: #ffffff; border-top: 1px solid #ececec">
      <p class="text-sm" style="color: #757575">
        Copyright &copy; {{ year }}
        <a href="#home" class="transition-colors hover:text-[#29ca8e]" style="color: #202020" @click.prevent="scrollTo('home')">{{ c.name }}</a>
        <span class="mx-1">|</span>
        <router-link to="/legal/terms" class="transition-colors hover:text-[#29ca8e]" style="color: #202020">Terms</router-link>
        <span class="mx-1">|</span>
        <router-link to="/legal/privacy" class="transition-colors hover:text-[#29ca8e]" style="color: #202020">Privacy</router-link>
      </p>
    </footer>

    <!-- ================= CONTACT FORM MODAL ================= -->
    <Dialog v-model:open="contactOpen">
      <DialogContent class="max-w-lg">
        <template v-if="!sent">
          <DialogHeader>
            <DialogTitle>Get in touch</DialogTitle>
            <DialogDescription>
              Tell us about your project and we will reach out to discuss next steps.
            </DialogDescription>
          </DialogHeader>

          <div class="space-y-4 py-2">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700">First Name <span class="text-red-500">*</span></label>
                <input
                  v-model="form.firstName"
                  type="text"
                  class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-[#29ca8e] focus:outline-none focus:ring-1 focus:ring-[#29ca8e]"
                  placeholder="Juan"
                />
                <p v-if="fieldErrors.firstName" class="mt-1 text-xs text-red-500">{{ fieldErrors.firstName }}</p>
              </div>
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700">Last Name <span class="text-red-500">*</span></label>
                <input
                  v-model="form.lastName"
                  type="text"
                  class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-[#29ca8e] focus:outline-none focus:ring-1 focus:ring-[#29ca8e]"
                  placeholder="Dela Cruz"
                />
                <p v-if="fieldErrors.lastName" class="mt-1 text-xs text-red-500">{{ fieldErrors.lastName }}</p>
              </div>
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700">Email <span class="text-red-500">*</span></label>
              <input
                v-model="form.email"
                type="email"
                class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-[#29ca8e] focus:outline-none focus:ring-1 focus:ring-[#29ca8e]"
                placeholder="you@company.com"
              />
              <p v-if="fieldErrors.email" class="mt-1 text-xs text-red-500">{{ fieldErrors.email }}</p>
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700">Phone <span class="text-red-500">*</span></label>
              <input
                v-model="form.phone"
                type="tel"
                class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-[#29ca8e] focus:outline-none focus:ring-1 focus:ring-[#29ca8e]"
                placeholder="+63 917 000 0000"
              />
              <p v-if="fieldErrors.phone" class="mt-1 text-xs text-red-500">{{ fieldErrors.phone }}</p>
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700">How can we help? <span class="text-red-500">*</span></label>
              <textarea
                v-model="form.message"
                rows="4"
                class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-[#29ca8e] focus:outline-none focus:ring-1 focus:ring-[#29ca8e]"
                placeholder="Briefly describe your project or requirement…"
              ></textarea>
              <p v-if="fieldErrors.message" class="mt-1 text-xs text-red-500">{{ fieldErrors.message }}</p>
            </div>
            <p v-if="sendError" class="text-sm text-red-500">{{ sendError }}</p>
          </div>

          <DialogFooter class="gap-2 sm:gap-2">
            <button
              type="button"
              class="rounded-full border border-gray-300 px-5 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50"
              @click="contactOpen = false"
            >
              Cancel
            </button>
            <button
              type="button"
              class="rounded-full bg-[#29ca8e] px-6 py-2 text-sm font-semibold text-white transition-colors hover:bg-[#1fa774] disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="sending"
              @click="submitContact"
            >
              {{ sending ? 'Sending…' : 'Send Message' }}
            </button>
          </DialogFooter>
        </template>

        <template v-else>
          <div class="flex flex-col items-center py-8 text-center">
            <span class="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-[#29ca8e]/10 text-2xl" style="color: #29ca8e">
              <i class="fa-solid fa-circle-check"></i>
            </span>
            <h3 class="mb-2 text-xl font-semibold" style="color: #202020">Message sent!</h3>
            <p class="mb-6 max-w-sm text-sm leading-6" style="color: #757575">
              Thank you for reaching out. Our team will get back to you at
              <span class="font-semibold" style="color: #202020">{{ sentTo }}</span> as soon as we can.
            </p>
            <button
              type="button"
              class="rounded-full bg-[#29ca8e] px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[#1fa774]"
              @click="contactOpen = false"
            >
              Close
            </button>
          </div>
        </template>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css?family=Open+Sans:300,300i,400,600,700');

/* Section eyebrow label above headings */
.section-eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 12px;
  font-weight: 700;
  color: #29ca8e;
  margin: 0 0 6px;
}

/* Hero CTA — matches the www.secphils.com "Get Started" button */
.hero-btn {
  background: transparent;
  border: 4px solid #ffffff;
  border-radius: 16px;
  color: #ffffff;
  cursor: pointer;
  font-size: 30px;
  font-weight: 400;
  padding: 15px 50px;
  transition: 0.5s;
}
.hero-btn:hover {
  background: transparent;
  border-color: #29ca8e;
  color: #29ca8e;
}

/* Smaller CTA used inside service tabs */
.hero-btn-sm {
  background: #29ca8e;
  border: 0;
  border-radius: 14px;
  color: #ffffff;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  padding: 12px 30px;
  transition: 0.3s;
}
.hero-btn-sm:hover {
  background: #1fa774;
}

/* Modernized service tab buttons */
.svc-tab {
  border: 1px solid #e8e8e8;
  border-radius: 14px;
  background: #ffffff;
  color: #575757;
  cursor: pointer;
  padding: 16px 10px 14px;
  text-align: center;
  transition: all 0.25s;
}
.svc-tab:hover {
  border-color: #29ca8e;
  color: #202020;
}
.svc-tab.is-active {
  border-color: #29ca8e;
  background: #f0fbf7;
  color: #147a55;
  box-shadow: 0 4px 14px rgba(41, 202, 142, 0.16);
}

/* Check bullets inside the "Other Services" list */
.svc-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border-radius: 50%;
  background: rgba(41, 202, 142, 0.12);
  color: #29ca8e;
  font-size: 11px;
  margin-top: 4px;
}

/* Social pills (About) */
.social-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e8e8e8;
  border-radius: 999px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #353535;
  text-decoration: none;
  background: #ffffff;
  transition: all 0.2s;
}
.social-chip:hover {
  border-color: #29ca8e;
  color: #147a55;
  background: #f0fbf7;
}
.social-chip i {
  color: #29ca8e;
  font-size: 15px;
}

/* Round social dots (About collage) */
.social-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(41, 202, 142, 0.12);
  color: #29ca8e;
  font-size: 17px;
  text-decoration: none;
  transition: all 0.2s;
}
.social-dot:hover {
  background: #29ca8e;
  color: #ffffff;
}

/* About badges */
.about-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  border-radius: 12px;
  background: rgba(41, 202, 142, 0.12);
  color: #29ca8e;
  font-size: 19px;
}

/* Say hello cards (button variant for the email card) */
.contact-card {
  display: block;
  background: #f9f9f9;
  padding: 24px 16px;
  border-radius: 14px;
  text-decoration: none;
  border: 0;
  width: 100%;
  text-align: center;
  transition: box-shadow 0.25s, transform 0.25s;
  cursor: pointer;
  font-family: inherit;
  appearance: none;
  -webkit-appearance: none;
  color: inherit;
  line-height: 1.4;
}
.contact-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

/* Green pill button (template .section-btn) */
.section-btn {
  background: #29ca8e;
  border: 0;
  border-radius: 50px;
  color: #ffffff;
  cursor: pointer;
  font-size: 16px;
  padding: 12px 30px;
  transition: 0.5s 0.2s;
}
.section-btn:hover {
  background: #202020;
}
</style>
