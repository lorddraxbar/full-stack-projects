<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useGetLanding, type LandingCompany, type LandingReview } from '@/services/api'

const router = useRouter()

// ---------- Company profile (Admin Panel > Company Settings) ----------
const company = ref<LandingCompany | null>(null)

// Fallbacks mirror www.secphils.com
const fallback = {
  name: 'Strategic Engineering Consultancy',
  phone: '+63.945.510.5172',
  email: 'manager@secphils.com',
  facebook: 'https://www.facebook.com/strategicengineeringconsultancy/',
  description:
    'We are a Filipino company, owned and operated by highly competitive engineers. Each professional on our team has multiple years of industry experience with very high attention to quality.',
}

const c = computed(() => ({
  name: company.value?.name?.trim() || fallback.name,
  phone: company.value?.phone?.trim() || fallback.phone,
  email: company.value?.email?.trim() || fallback.email,
  facebook: company.value?.socialLinks?.trim() || fallback.facebook,
  description: company.value?.description?.trim() || fallback.description,
}))

const year = new Date().getFullYear()

// ---------- Navbar ----------
const scrolled = ref(false)
const menuOpen = ref(false)
const onScroll = () => {
  scrolled.value = window.scrollY > 60
}
const navLinks = [
  { id: 'home', label: 'Home' },
  { id: 'services', label: 'Services' },
  { id: 'about', label: 'About' },
]
const scrollTo = (id: string) => {
  menuOpen.value = false
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

// ---------- Services (ported from www.secphils.com) ----------
interface ServiceTab {
  key: string
  label: string
  title: string
  paras?: string[]
  list?: string[]
}
const serviceTabs: ServiceTab[] = [
  {
    key: 'ecc',
    label: 'ECC',
    title: 'Environmental Compliance Certificate (ECC)',
    paras: [
      'The Environmental Compliance Certificate or ECC refers to the document issued by the DENR/Environmental Management Board (EMB) that allows the project to proceed to the next stage of project planning, which is the acquisition of approvals from other government agencies and LGUs, after which the project can start implementation.',
      'It certifies that the proponent has complied with the requirements of the Environmental Impact Statement (EIS) system and that the proposed project will not cause a significant negative impact on the environment. It also certifies that the proponent is committed to implement its approved Environment Management Plan. Requirements for ECC application depend on the type and location of project being developed.',
    ],
  },
  {
    key: 'cnc',
    label: 'CNC',
    title: 'Certificate of Non-Coverage (CNC)',
    paras: [
      'The Certificate of Non-Coverage is a document issued by the DENR/Environmental Management Board (EMB) certifying that, based on the submitted project description, the project is not covered by the EIS (Environmental Impact Statement) system and is not required to secure an ECC. This covers projects which are not critical to the environment.',
    ],
  },
  {
    key: 'other',
    label: 'Other Services',
    title: 'Other Services',
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

onMounted(() => {
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
            <a
              :href="`mailto:${c.email}`"
              class="ml-4 text-sm transition-colors"
              :class="scrolled ? 'text-[#575757] hover:text-[#29ca8e]' : 'text-[#f0f0f0] hover:text-[#29ca8e]'"
            >
              {{ c.email }} | {{ c.phone }}
            </a>
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
        <a :href="`mailto:${c.email}`" class="block px-6 py-3 text-sm text-[#575757]">
          {{ c.email }} | {{ c.phone }}
        </a>
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
        <button class="hero-btn" @click="scrollTo('contact')">Get Started</button>
      </div>
    </section>

    <!-- ================= SERVICES ================= -->
    <section id="services" class="scroll-mt-0 py-20" style="background: #f9f9f9">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10">
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">Our Services</h1>
        </div>

        <div class="flex flex-col gap-10 lg:flex-row">
          <!-- Tabs + content -->
          <div class="lg:w-[58%]">
            <ul class="flex flex-wrap gap-x-8 gap-y-2">
              <li v-for="tab in serviceTabs" :key="tab.key">
                <button
                  class="border-b-[3px] pb-2 text-lg transition-colors"
                  :class="
                    activeTab.key === tab.key
                      ? 'border-[#29ca8e] text-[#202020]'
                      : 'border-transparent text-[#999999] hover:border-[#29ca8e] hover:text-[#202020]'
                  "
                  @click="activeTab = tab"
                >
                  {{ tab.label }}
                </button>
              </li>
            </ul>

            <div class="mt-8 min-h-[280px]">
              <h2 class="mb-2 font-light text-2xl sm:text-3xl" style="color: #353535">{{ activeTab.title }}</h2>
              <p
                v-for="(p, i) in activeTab.paras"
                :key="i"
                class="mb-2.5 text-sm leading-6"
                style="color: #757575"
              >
                {{ p }}
              </p>
              <ul v-if="activeTab.list" class="space-y-3 pt-2">
                <li
                  v-for="item in activeTab.list"
                  :key="item"
                  class="font-light text-xl sm:text-2xl"
                  style="color: #353535"
                >
                  {{ item }}
                </li>
              </ul>
              <div class="mt-7">
                <button class="hero-btn-sm" @click="scrollTo('contact')">Get Started</button>
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

    <!-- ================= ABOUT ================= -->
    <section id="about" class="py-20" style="background: #f9f9f9">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10 text-center">
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">About</h1>
        </div>

        <div class="grid gap-6 md:grid-cols-3">
          <!-- Card 1: who we are -->
          <div class="bg-white">
            <img src="/images/landing/team-image1.jpg" alt="Who we are" class="aspect-[4/3] w-full object-cover" />
            <div class="relative px-8 py-8">
              <span class="bubble-tab" />
              <p class="text-sm leading-6" style="color: #757575">{{ c.description }}</p>
            </div>
          </div>

          <!-- Card 2: visit us on Facebook (box on top, photo below) -->
          <div class="flex flex-col bg-white">
            <div class="relative px-8 py-8 text-center">
              <span class="bubble-tab-down" />
              <h2 class="mb-2 font-light text-2xl" style="color: #353535">Visit us on</h2>
              <a :href="c.facebook" target="_blank" rel="noopener" class="inline-block text-[64px] leading-none" style="color: #202020" :title="'Facebook — ' + c.name">
                <i class="fab fa-facebook"></i>
              </a>
            </div>
            <img src="/images/landing/team-image2.jpg" alt="Contact" class="aspect-[4/3] w-full object-cover" />
          </div>

          <!-- Card 3: contact -->
          <div class="bg-white">
            <img src="/images/landing/team-image3.jpg" alt="Contact" class="aspect-[4/3] w-full object-cover" />
            <div class="relative px-8 py-8">
              <span class="bubble-tab" />
              <h4 class="mb-1.5 text-base font-light" style="color: #353535">
                Email: <strong class="font-semibold" style="color: #202020">{{ c.email }}</strong>
              </h4>
              <h4 class="text-base font-light" style="color: #353535">
                Phone: <strong class="font-semibold" style="color: #202020">{{ c.phone }}</strong>
              </h4>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ================= REVIEWS ================= -->
    <section id="reviews" class="py-20" style="background: #ffffff">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10 text-center">
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">What People Say</h1>
        </div>

        <div v-if="!loading && reviews.length" class="grid gap-6 md:grid-cols-3">
          <div v-for="r in reviews" :key="r.id" class="p-8" style="background: #f9f9f9">
            <div class="mb-3 text-lg" style="color: #29ca8e">{{ stars(r.rating) }}</div>
            <h3 class="mb-4 font-light text-xl leading-snug" style="color: #353535">{{ r.title }}</h3>
            <p class="text-sm leading-6" style="color: #757575">{{ r.body }}</p>
            <p class="mt-4 text-sm font-semibold" style="color: #202020">
              {{ r.customerName || 'Client' }}
              <span v-if="r.projectName" class="font-normal" style="color: #757575"> — {{ r.projectName }}</span>
            </p>
          </div>
        </div>

        <div v-else-if="!loading" class="text-center text-sm" style="color: #999999">
          Client reviews will appear here once they are approved in the portal.
        </div>
      </div>
    </section>

    <!-- ================= CONTACT ================= -->
    <section id="contact" class="py-20 text-center" style="background: #ffffff">
      <div class="mx-auto max-w-[1140px] px-4">
        <div class="pb-10">
          <h1 class="font-light text-3xl sm:text-4xl md:text-[3em]" style="color: #202020">Say hello to us</h1>
        </div>

        <div class="mx-auto grid max-w-3xl gap-6 sm:grid-cols-3">
          <a :href="`mailto:${c.email}`" class="p-6 transition-shadow hover:shadow-md" style="background: #f9f9f9">
            <i class="fas fa-envelope mb-3 text-2xl" style="color: #29ca8e" />
            <p class="mb-1 text-sm font-semibold" style="color: #202020">Email</p>
            <p class="break-all text-sm" style="color: #757575">{{ c.email }}</p>
          </a>
          <a :href="`tel:${c.phone.replace(/\s/g, '')}`" class="p-6 transition-shadow hover:shadow-md" style="background: #f9f9f9">
            <i class="fas fa-phone mb-3 text-2xl" style="color: #29ca8e" />
            <p class="mb-1 text-sm font-semibold" style="color: #202020">Phone</p>
            <p class="text-sm" style="color: #757575">{{ c.phone }}</p>
          </a>
          <a :href="c.facebook" target="_blank" rel="noopener" class="p-6 transition-shadow hover:shadow-md" style="background: #f9f9f9">
            <i class="fab fa-facebook mb-3 text-2xl" style="color: #29ca8e" />
            <p class="mb-1 text-sm font-semibold" style="color: #202020">Facebook</p>
            <p class="truncate text-sm" style="color: #757575">{{ c.name }}</p>
          </a>
        </div>

        <div class="mt-10">
          <button class="section-btn" @click="router.push('/auth/login')">Portal Login</button>
        </div>
      </div>
    </section>

    <!-- ================= FOOTER ================= -->
    <footer class="py-10 text-center" style="background: #ffffff">
      <p class="text-sm" style="color: #757575">
        Copyright &copy; {{ year }}
        <a href="#" class="transition-colors hover:text-[#29ca8e]" style="color: #202020" @click.prevent="scrollTo('home')">{{ c.name }}</a>
        <span class="mx-1">|</span>
        <a href="https://www.secphils.com/terms.html" target="_blank" rel="noopener" class="transition-colors hover:text-[#29ca8e]" style="color: #202020">Terms</a>
        <span class="mx-1">|</span>
        <a href="https://www.secphils.com/privacy.html" target="_blank" rel="noopener" class="transition-colors hover:text-[#29ca8e]" style="color: #202020">Privacy</a>
      </p>
    </footer>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css?family=Open+Sans:300,300i,400,600,700');

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
  background: transparent;
  border: 2px solid #29ca8e;
  border-radius: 16px;
  color: #29ca8e;
  cursor: pointer;
  font-size: 14px;
  font-weight: 400;
  padding: 10px 28px;
  transition: 0.5s;
}
.hero-btn-sm:hover {
  background: #29ca8e;
  color: #ffffff;
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

/* White speech-bubble notch above the About info panels (template team-thumb-up:after) */
.bubble-tab {
  position: absolute;
  top: -15px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-right: 15px solid transparent;
  border-left: 15px solid transparent;
  border-bottom: 15px solid #ffffff;
}

/* Downward notch under the "Visit us on" box (template team-thumb-down:after) */
.bubble-tab-down {
  position: absolute;
  bottom: -15px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-right: 15px solid transparent;
  border-left: 15px solid transparent;
  border-top: 15px solid #ffffff;
}
</style>
