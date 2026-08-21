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
      <a class="legal-back" href="/">
        <i class="fa-solid fa-arrow-left"></i> Back to home
      </a>
      <div class="legal-brand">
        <img src="/images/landing/seclogo.png" alt="Strategic Engineering Consultancy logo" />
        <span>Strategic Engineering Consultancy - Philippines</span>
      </div>
    </header>

    <main class="legal-main">
      <div class="legal-doc">
        <p class="legal-eyebrow">Legal</p>
        <h1>{{ pageTitle }}</h1>
        <p class="legal-meta">Last updated: <time datetime="2026-08">August 2026</time></p>

        <!-- ================= TERMS ================= -->
        <div v-if="isTerms" class="legal-body">
          <p>
            Please read these Terms of Service (these "Terms") carefully before using
            this website operated by <strong>Strategic Engineering Consultancy</strong>
            ("we", "us", or "our") (the "Website"). By accessing or using the Website,
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
            The Website and its content are provided "as is" and "as available" without
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
          <p>
            This Privacy Policy describes how Strategic Engineering Consultancy ("we", "us",
            or "our") collects, uses, and shares information when you use our website
            secphils.com (the "Website"). By accessing or using the Website, you consent to
            the data practices described in this Privacy Policy.
          </p>

          <h2>1. Information We Collect</h2>
          <ul>
            <li>
              <strong>1.1. Personal Information:</strong> We may collect personal
              information that you voluntarily provide to us when you use the Website, such
              as your name, email address, and contact information.
            </li>
            <li>
              <strong>1.2. Usage Data:</strong> We may also collect information about your
              usage of the Website, including but not limited to your IP address, browser
              type, operating system, and pages visited.
            </li>
            <li>
              <strong>1.3. Cookies:</strong> We may use cookies and similar tracking
              technologies to collect information about your interactions with the Website.
              You can control the use of cookies through your browser settings.
            </li>
          </ul>

          <h2>2. How We Use Your Information</h2>
          <ul>
            <li>
              <strong>2.1. Provide Services:</strong> We may use your information to provide
              and personalize the services offered on the Website, communicate with you, and
              respond to your inquiries.
            </li>
            <li>
              <strong>2.2. Analytics:</strong> We may use your information to analyze trends,
              administer the Website, and gather demographic information about our user base.
            </li>
            <li>
              <strong>2.3. Marketing:</strong> With your consent, we may send you promotional
              emails about our products and services or other information that we think you
              may find interesting.
            </li>
          </ul>

          <h2>3. Information Sharing</h2>
          <ul>
            <li>
              <strong>3.1. Legal Compliance:</strong> We may disclose your information to
              comply with applicable laws, regulations, legal processes, or government
              requests.
            </li>
          </ul>

          <h2>4. Data Security</h2>
          <ul>
            <li>
              <strong>4.1. Security Measures:</strong> We take reasonable measures to protect
              your information from unauthorized access, disclosure, alteration, or
              destruction.
            </li>
            <li>
              <strong>4.2. No Guarantee:</strong> However, no method of transmission over the
              internet or electronic storage is 100% secure, and we cannot guarantee the
              absolute security of your information.
            </li>
          </ul>

          <h2>5. Your Rights</h2>
          <ul>
            <li>
              <strong>5.1. Access and Correction:</strong> You have the right to access and
              correct any personal information we hold about you. You may also request that
              we delete your personal information, subject to certain exceptions.
            </li>
            <li>
              <strong>5.2. Opt-Out:</strong> You may opt-out of receiving promotional
              communications from us by following the instructions provided in such
              communications or by contacting us directly.
            </li>
          </ul>

          <h2>6. Changes to this Privacy Policy</h2>
          <ul>
            <li>
              <strong>6.1. Updates:</strong> We may update this Privacy Policy from time to
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
          Copyright &copy; 2020 Strategic Engineering Consultancy
        </p>
      </div>
    </main>
  </div>

  <style scoped>
  .legal-page {
    min-height: 100vh;
    background: #f9f9f9;
    color: #1f2937;
  }

  .legal-header {
    background: #ffffff;
    border-bottom: 1px solid #e5e7eb;
    padding: 18px 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    position: sticky;
    top: 0;
    z-index: 10;
  }

  .legal-back {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #29ca8e;
    text-decoration: none;
    font-size: 15px;
    font-weight: 600;
    white-space: nowrap;
  }

  .legal-back:hover { color: #1fa774; }

  .legal-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }

  .legal-brand img {
    height: 38px;
    width: auto;
  }

  .legal-brand span {
    font-size: 14px;
    font-weight: 600;
    color: #374151;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .legal-main {
    max-width: 860px;
    margin: 0 auto;
    padding: 48px 24px 80px;
  }

  .legal-doc {
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 14px;
    padding: 48px 56px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  }

  .legal-eyebrow {
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 12px;
    font-weight: 700;
    color: #29ca8e;
    margin: 0 0 8px;
  }

  .legal-doc h1 {
    font-size: 34px;
    font-weight: 700;
    color: #111827;
    margin: 0 0 8px;
    line-height: 1.2;
  }

  .legal-meta {
    color: #6b7280;
    font-size: 14px;
    margin: 0 0 28px;
    padding-bottom: 24px;
    border-bottom: 1px solid #e5e7eb;
  }

  .legal-body h2 {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    margin: 32px 0 12px;
  }

  .legal-body p {
    font-size: 15.5px;
    line-height: 1.75;
    color: #374151;
    margin: 0 0 16px;
  }

  .legal-body ul {
    margin: 0 0 16px;
    padding-left: 4px;
    list-style: none;
  }

  .legal-body ul li {
    font-size: 15.5px;
    line-height: 1.7;
    color: #374151;
    margin: 0 0 12px;
    padding-left: 18px;
    position: relative;
  }

  .legal-body ul li::before {
    content: '';
    position: absolute;
    left: 0;
    top: 9px;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #29ca8e;
  }

  .legal-body a {
    color: #29ca8e;
    text-decoration: underline;
  }

  .legal-body a:hover { color: #1fa774; }

  .legal-copy {
    margin-top: 40px;
    padding-top: 20px;
    border-top: 1px solid #e5e7eb;
    color: #9ca3af;
    font-size: 13px;
  }

  @media (max-width: 640px) {
    .legal-header {
      flex-direction: column;
      gap: 10px;
      align-items: flex-start;
    }
    .legal-main { padding: 24px 14px 48px; }
    .legal-doc { padding: 28px 22px; }
    .legal-doc h1 { font-size: 26px; }
  }
  </style>
</template>
