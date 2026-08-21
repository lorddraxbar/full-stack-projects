<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGetLanding } from '@/services/api'

const props = defineProps<{ kind: 'terms' | 'privacy' }>()

const company = ref<any>(null)
const loading = ref(true)

const contactEmail = computed(() => {
  const raw: string = company.value?.email ?? 'manager@secphils.com'
  return raw.split(/[,;\s]+/).map(s => s.trim()).find(s => s.includes('@')) || 'manager@secphils.com'
})

const isTerms = computed(() => props.kind === 'terms')
const pageTitle = computed(() => (isTerms.value ? 'Terms of Service' : 'Privacy Policy'))

onMounted(async () => {
  try {
    const data = await useGetLanding()
    company.value = data.company
  } catch {
    /* fallbacks below */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="legal-page">
    <header class="legal-header">
      <a class="legal-logo" href="/" aria-label="Home — Strategic Engineering Consultancy">
        <img src="/images/landing/seclogo.png" alt="Strategic Engineering Consultancy" />
        <span class="legal-name">Strategic Engineering Consultancy</span>
      </a>
    </header>

    <main class="legal-main">
      <div class="legal-doc">
        <p class="legal-eyebrow">Legal</p>
        <h1 class="legal-title">{{ pageTitle }}</h1>
        <div class="legal-title-accent"></div>
        <p class="legal-meta">Last updated: <time datetime="2026-08">August 2026</time></p>

        <!-- ================= TERMS ================= -->
        <div v-if="isTerms" class="legal-body">
          <p class="legal-intro">
            Please read these Terms of Service (these &ldquo;Terms&rdquo;) carefully before using
            this website operated by <strong>Strategic Engineering Consultancy</strong>
            (&ldquo;we&rdquo;, &ldquo;us&rdquo;, or &ldquo;our&rdquo;) (the &ldquo;Website&rdquo;). By accessing or using the Website,
            you agree to be bound by these Terms. If you do not agree, do not use the
            Website.
          </p>

          <h2>1. Use of the Website</h2>
          <p>
            You may use the Website only for lawful purposes and in a way that does not
            violate these Terms or any applicable laws or regulations. You are responsible
            for your conduct and any data, text, or other content you submit to or through
            the Website.
          </p>

          <h2>2. Intellectual Property</h2>
          <p>
            All content on the Website, including text, graphics, logos, images, software,
            and other material, is the property of Strategic Engineering Consultancy or its
            licensors and is protected by applicable intellectual property laws. You may not
            copy, modify, distribute, or create derivative works without our prior written
            consent.
          </p>

          <h2>3. No Professional Relationship</h2>
          <p>
            Information provided on the Website is for general informational purposes only
            and does not constitute professional advice. Viewing the Website does not create
            any consulting, agency, or professional relationship between you and
            Strategic Engineering Consultancy.
          </p>

          <h2>4. Disclaimer of Warranties</h2>
          <p>
            The Website and its content are provided &ldquo;as is&rdquo; and &ldquo;as available&rdquo; without
            warranties of any kind, either express or implied, including but not limited to
            implied warranties of merchantability, fitness for a particular purpose, and
            non-infringement. We do not guarantee that the Website will be uninterrupted,
            secure, or error-free.
          </p>

          <h2>5. Limitation of Liability</h2>
          <p>
            To the fullest extent permitted by law, Strategic Engineering Consultancy shall
            not be liable for any indirect, incidental, special, consequential, or punitive
            damages, or any loss of profits, data, or goodwill, arising out of or related to
            your use of, or inability to use, the Website.
          </p>

          <h2>6. Third-Party Links</h2>
          <p>
            The Website may contain links to third-party websites or services that are not
            operated by us. We have no control over, and assume no responsibility for, the
            content or practices of any third-party sites.
          </p>

          <h2>7. Changes to These Terms</h2>
          <p>
            We may revise these Terms from time to time. Any changes will be effective
            immediately upon posting the updated Terms on the Website. Your continued use of
            the Website after any such changes constitutes your acceptance of the revised
            Terms.
          </p>

          <h2>8. Contact</h2>
          <p>
            If you have any questions about these Terms of Service, please contact us at
            <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a>.
          </p>
        </div>

        <!-- ================= PRIVACY ================= -->
        <div v-else class="legal-body">
          <p class="legal-intro">
            This Privacy Policy describes how Strategic Engineering Consultancy (&ldquo;we&rdquo;, &ldquo;us&rdquo;,
            or &ldquo;our&rdquo;) collects, uses, and shares information when you use our website
            secphils.com (the &ldquo;Website&rdquo;). By accessing or using the Website, you consent to
            the data practices described in this Privacy Policy.
          </p>

          <h2>1. Information We Collect</h2>
          <ul>
            <li>
              <strong>Personal Information:</strong> We may collect personal
              information that you voluntarily provide to us when you use the Website, such
              as your name, email address, and contact information.
            </li>
            <li>
              <strong>Usage Data:</strong> We may also collect information about your
              usage of the Website, including but not limited to your IP address, browser
              type, operating system, and pages visited.
            </li>
            <li>
              <strong>Cookies:</strong> We may use cookies and similar tracking
              technologies to collect information about your interactions with the Website.
              You can control the use of cookies through your browser settings.
            </li>
          </ul>

          <h2>2. How We Use Your Information</h2>
          <ul>
            <li>
              <strong>Provide Services:</strong> We may use your information to provide
              and personalize the services offered on the Website, communicate with you, and
              respond to your inquiries.
            </li>
            <li>
              <strong>Analytics:</strong> We may use your information to analyze trends,
              administer the Website, and gather demographic information about our user base.
            </li>
            <li>
              <strong>Marketing:</strong> With your consent, we may send you promotional
              emails about our products and services or other information that we think you
              may find interesting.
            </li>
          </ul>

          <h2>3. Information Sharing</h2>
          <ul>
            <li>
              <strong>Legal Compliance:</strong> We may disclose your information to
              comply with applicable laws, regulations, legal processes, or government
              requests.
            </li>
          </ul>

          <h2>4. Data Security</h2>
          <ul>
            <li>
              <strong>Security Measures:</strong> We take reasonable measures to protect
              your information from unauthorized access, disclosure, alteration, or
              destruction.
            </li>
            <li>
              <strong>No Guarantee:</strong> However, no method of transmission over the
              internet or electronic storage is 100% secure, and we cannot guarantee the
              absolute security of your information.
            </li>
          </ul>

          <h2>5. Your Rights</h2>
          <ul>
            <li>
              <strong>Access and Correction:</strong> You have the right to access and
              correct any personal information we hold about you. You may also request that
              we delete your personal information, subject to certain exceptions.
            </li>
            <li>
              <strong>Opt-Out:</strong> You may opt-out of receiving promotional
              communications from us by following the instructions provided in such
              communications or by contacting us directly.
            </li>
          </ul>

          <h2>6. Changes to this Privacy Policy</h2>
          <ul>
            <li>
              <strong>Updates:</strong> We may update this Privacy Policy from time to
              time by posting a new version on the Website. You should check this page
              periodically to review any changes.
            </li>
          </ul>

          <h2>7. Contact Us</h2>
          <p>
            If you have any questions about this Privacy Policy, please contact us at
            <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a>.
          </p>

          <p>
            By using the Website, you acknowledge that you have read, understood, and agree
            to be bound by this Privacy Policy.
          </p>
        </div>

        <p class="legal-copy">
          Copyright &copy; 2026 Strategic Engineering Consultancy
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* Page root — svh (not vh) so the page height stays stable on mobile rotation,
   matching the landing page's min-h-viewport convention. */
.legal-page {
  min-height: 100vh;
  min-height: 100svh;
  background: #f9f9f9;
  color: #1f2937;
  font-family: 'Raleway', 'Open Sans', ui-sans-serif, system-ui, sans-serif;
}

/* ---------- Header: logo + company name (mirrors the landing navbar) ---------- */
.legal-header {
  background: #ffffff;
  border-bottom: 1px solid #ececec;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: 10;
}

.legal-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
}

.legal-logo img {
  height: 52px;
  width: auto;
  display: block;
}

.legal-name {
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: #252525;
  white-space: nowrap;
}

@media (max-width: 1023px) {
  .legal-name { display: none; }
  .legal-logo img { height: 44px; }
}

/* ---------- Document ---------- */
.legal-main {
  max-width: 860px;
  margin: 0 auto;
  padding: 44px 24px 72px;
}

.legal-doc {
  background: #ffffff;
  border: 1px solid #ececec;
  border-radius: 16px;
  padding: 48px 56px;
  box-shadow: 0 6px 24px rgba(17, 24, 39, 0.04);
}

.legal-eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 12px;
  font-weight: 700;
  color: #29ca8e;
  margin: 0 0 10px;
}

.legal-title {
  font-size: 34px;
  font-weight: 300;
  color: #202020;
  margin: 0;
  line-height: 1.2;
}

.legal-title-accent {
  width: 56px;
  height: 3px;
  border-radius: 2px;
  background: #29ca8e;
  margin: 14px 0 16px;
}

.legal-meta {
  color: #8a8a8a;
  font-size: 13.5px;
  margin: 0 0 28px;
  padding-bottom: 22px;
  border-bottom: 1px solid #ececec;
}

/* ---------- Body typography ---------- */
.legal-body .legal-intro,
.legal-body p {
  font-size: 15.5px;
  line-height: 1.8;
  color: #4b5563;
  margin: 0 0 18px;
}

.legal-body .legal-intro {
  font-size: 16px;
  color: #374151;
}

.legal-body strong {
  color: #1f2937;
  font-weight: 700;
}

/* Section headings — distinct, elegant, clearly separated */
.legal-body h2 {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 600;
  color: #202020;
  margin: 36px 0 14px;
  line-height: 1.4;
}

.legal-body h2::before {
  content: '';
  flex: none;
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: #29ca8e;
}

.legal-body ul {
  margin: 0 0 18px;
  padding: 0;
  list-style: none;
}

.legal-body ul li {
  font-size: 15.5px;
  line-height: 1.75;
  color: #4b5563;
  margin: 0 0 12px;
  padding: 0 0 12px 22px;
  position: relative;
}

.legal-body ul li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 10px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #29ca8e;
}

.legal-body ul li:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
}

.legal-body a {
  color: #1f8a70;
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid rgba(31, 138, 112, 0.35);
}

.legal-body a:hover { color: #166a55; }

.legal-copy {
  margin: 44px 0 0;
  padding-top: 20px;
  border-top: 1px solid #ececec;
  color: #9ca3af;
  font-size: 13px;
}

@media (max-width: 640px) {
  .legal-header { padding: 12px 16px; }
  .legal-name { font-size: 12.5px; }
  .legal-main { padding: 22px 14px 48px; }
  .legal-doc { padding: 30px 22px; border-radius: 14px; }
  .legal-title { font-size: 27px; }
  .legal-body h2 { font-size: 17px; margin-top: 30px; }
}
</style>
