<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useGetLanding } from '@/services/api'

const props = defineProps<{ kind: 'terms' | 'privacy' }>()

const company = ref<any>(null)
const loading = ref(true)

const toHex = (v: string | undefined, fb: string) =>
  typeof v === 'string' && /^#[0-9a-f]{6}$/i.test(v.trim()) ? v.trim() : fb
const hexToRgb = (hex: string): [number, number, number] => {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}
const shade = (hex: string, pct: number): string => {
  const [r, g, b] = hexToRgb(hex)
  const t = pct < 0 ? 0 : 255
  const m = Math.abs(pct)
  const f = (x: number) => Math.round(x + (t - x) * m)
  return `#${[f(r), f(g), f(b)].map((x) => x.toString(16).padStart(2, '0')).join('')}`
}
const brandStyle = computed<Record<string, string>>(() => {
  const primary = toHex(company.value?.brandPrimary, '#29ca8e')
  return {
    '--bsp': primary,
    '--bsp-soft': `rgba(${hexToRgb(primary).join(', ')}, 0.12)`,
    '--bsp-link': shade(primary, -0.2),
    '--bsp-link-hover': shade(primary, -0.4),
  }
})

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
  <div class="legal-page" :style="brandStyle">
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
        <p class="legal-meta">Last updated: <time datetime="2026-09">September 2026</time></p>

        <!-- ================= TERMS ================= -->
        <div v-if="isTerms" class="legal-body">
          <p class="legal-intro">
            These Terms of Service (these &ldquo;Terms&rdquo;) govern your access to and use of the
            website and the client portal (together, the &ldquo;Services&rdquo;) operated by
            <strong>Strategic Engineering Consultancy</strong> (&ldquo;we&rdquo;, &ldquo;us&rdquo;, or
            &ldquo;our&rdquo;). By accessing or using the Services, you agree to be bound by these
            Terms. If you do not agree, do not use the Services. If you use the portal on
            behalf of a company, you represent that you are authorized to act for that company.
          </p>

          <h2>1. Accounts and Access</h2>
          <p>
            Portal access requires an account issued or approved by us. You must provide
            accurate information, keep your credentials confidential, and accept
            responsibility for all activity under your account. You must notify us promptly
            of any unauthorized use. We may suspend or deactivate accounts that violate
            these Terms, and we may restore them if the issue is resolved.
          </p>

          <h2>2. Acceptable Use</h2>
          <p>
            You may use the Services only for lawful purposes. You must not upload unlawful,
            infringing, or malicious content; attempt to disrupt, probe, or gain unauthorized
            access to the Services; scrape or resell portal data; or misrepresent your
            identity or affiliation.
          </p>

          <h2>3. Your Content and Our Content</h2>
          <p>
            Documents, messages, and other materials you submit through the portal remain
            yours. You grant us a limited license to host, store, process, and display them
            solely to provide the Services and perform your engagement. Portal software,
            design, text, and branding are ours or our licensors&rsquo; and may not be copied
            or distributed without our written consent. Ownership of consulting deliverables
            is governed by your engagement agreement.
          </p>

          <h2>4. Confidentiality</h2>
          <p>
            Project information exchanged through the portal is treated as confidential by
            both sides and used only to carry out the engagement, except where it is public,
            already known, or required to be disclosed by law.
          </p>

          <h2>5. Electronic Records and Signatures</h2>
          <p>
            Records, documents, and electronic signatures processed through the Services have
            the same legal effect as their paper equivalents to the extent permitted by
            applicable law.
          </p>

          <h2>6. Availability</h2>
          <p>
            The Services are provided on an &ldquo;as available&rdquo; basis. We may perform
            maintenance and may modify or discontinue features; we are not liable for
            interruptions beyond our reasonable control.
          </p>

          <h2>7. No Professional Advice Through the Website</h2>
          <p>
            General content on our public website is informational only and does not create a
            consulting, agency, or professional relationship. Professional advice is given
            only through a signed engagement.
          </p>

          <h2>8. Disclaimer of Warranties</h2>
          <p>
            The Services are provided &ldquo;as is&rdquo; and &ldquo;as available&rdquo; without warranties of any
            kind, express or implied, including merchantability, fitness for a particular
            purpose, and non-infringement. We do not guarantee that the Services will be
            uninterrupted, secure, or error-free.
          </p>

          <h2>9. Limitation of Liability</h2>
          <p>
            To the fullest extent permitted by law, we are not liable for any indirect,
            incidental, special, consequential, or punitive damages, or any loss of profits,
            data, or goodwill, arising from your use of or inability to use the Services, and
            our aggregate liability arising out of the Services is limited to the amount you
            paid us for the affected engagement in the twelve (12) months before the claim.
          </p>

          <h2>10. Termination</h2>
          <p>
            You may request account closure at any time. We may suspend or terminate access
            for violation of these Terms or upon completion of your engagement, subject to
            the data-retention practices described in our Privacy Policy. Sections on
            intellectual property, confidentiality, disclaimers, and liability survive
            termination.
          </p>

          <h2>11. Changes to These Terms</h2>
          <p>
            We may revise these Terms from time to time. Changes are effective upon posting;
            material changes will be announced through the portal where practicable.
            Continued use after posting constitutes acceptance.
          </p>

          <h2>12. Governing Law</h2>
          <p>
            These Terms are governed by the laws of the Republic of the Philippines. Any
            dispute shall be brought before the competent courts of the province where our
            principal office is located.
          </p>

          <h2>13. Contact</h2>
          <p>
            Questions about these Terms: <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a>.
          </p>
        </div>

        <!-- ================= PRIVACY ================= -->
        <div v-else class="legal-body">
          <p class="legal-intro">
            This Privacy Policy explains how <strong>Strategic Engineering Consultancy</strong>
            (&ldquo;we&rdquo;, &ldquo;us&rdquo;, or &ldquo;our&rdquo;), a Philippine company, collects, uses,
            discloses, and protects personal information when you use our public website or
            the SECPhils client portal (together, the &ldquo;Services&rdquo;). We act as the personal
            information controller and comply with the Republic Act No. 10173, or Data
            Privacy Act of 2012 (DPA), and its implementing rules. By using the Services,
            you consent to the practices described here.
          </p>

          <h2>1. Information We Collect</h2>
          <ul>
            <li>
              <strong>Account and company information:</strong> name, work email, phone
              number, role, and your company's name and contact details, provided when your
              account is created or by an administrator on your behalf.
            </li>
            <li>
              <strong>Project content:</strong> documents, messages, announcements, review
              responses, and other materials you submit through the portal.
            </li>
            <li>
              <strong>Automatic data:</strong> device and browser type, IP address, and logs
              of portal actions (time, feature, and actor) kept for security and audit
              purposes.
            </li>
            <li>
              <strong>Cookies and local storage:</strong> sign-in tokens are stored in your
              browser so you stay logged in. We do not run advertising or third-party
              tracking pixels on the Services.
            </li>
          </ul>

          <h2>2. How We Use Information</h2>
          <ul>
            <li>Provide the portal and perform your consulting engagement.</li>
            <li>Create and secure your account, including sign-in and two-factor authentication.</li>
            <li>Send service notifications by email or in-app alerts, per your preferences.</li>
            <li>Maintain security, investigate misuse, troubleshoot, and meet legal obligations.</li>
          </ul>

          <h2>3. How We Disclose Information</h2>
          <ul>
            <li>
              <strong>We never sell personal information.</strong> We share it with our
              authorized staff and, as needed, with trusted service providers — cloud
              hosting and storage, an electronic-signature provider, and an email delivery
              provider — each bound to confidentiality and permitted only to perform
              services for us.
            </li>
            <li>
              <strong>Within your organization:</strong> members of your company's project
              team can see the project information shared with them in the portal.
            </li>
            <li>
              <strong>Legal:</strong> we may disclose information to comply with law, legal
              process, or lawful government requests.
            </li>
          </ul>

          <h2>4. Hosting and Cross-Border Processing</h2>
          <p>
            The Services run on cloud infrastructure (Amazon Web Services). Your data may be
            stored and processed outside the Philippines; we rely on the providers'
            contractual and technical safeguards consistent with the DPA.
          </p>

          <h2>5. Data Retention</h2>
          <p>
            Personal and project data is kept while your account is active and your
            engagement is ongoing. Deactivated accounts and removed items are fully deleted
            after a short administrative recovery window (seven (7) days by default), unless
            a longer period is configured for your organization. Security-audit logs and
            records we must keep by law are retained as required.
          </p>

          <h2>6. Security</h2>
          <p>
            We use encrypted connections (HTTPS/TLS), salted password hashing, optional
            two-factor authentication, role-based access controls, and activity auditing. No
            method of transmission or storage is perfectly secure, and we cannot guarantee
            absolute security.
          </p>

          <h2>7. Your Rights Under the DPA</h2>
          <p>
            As a data subject in the Philippines, you have the right to be informed of
            processing; to access and correct your personal information; to erase or block
            it; to object to processing; to data portability; and to be indemnified for
            damages from unlawful processing. To exercise these rights, contact us at
            <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a>; we will respond
            within a reasonable period allowed by law. You also have the right to file a
            complaint with the National Privacy Commission (NPC).
          </p>

          <h2>8. Children</h2>
          <p>
            The Services are business tools intended for adults and are not directed at
            persons under eighteen (18) years of age.
          </p>

          <h2>9. Changes to this Privacy Policy</h2>
          <p>
            We may update this Policy from time to time by posting a revised version on the
            Services, with the effective date refreshed. Material changes will be announced
            through the portal where practicable.
          </p>

          <h2>10. Contact and Data Protection Officer</h2>
          <p>
            Questions, requests, or concerns about this Policy or your personal information
            may be directed to our Data Protection Officer at
            <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a>.
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
  color: var(--foreground);
  font-family: 'Raleway', 'Open Sans', ui-sans-serif, system-ui, sans-serif;
}

/* ---------- Header: logo + company name (mirrors the landing navbar) ---------- */
.legal-header {
  background: #ffffff;
  border-bottom: 1px solid #ececec;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
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
  color: var(--foreground);
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
  box-shadow: 0 6px 24px rgba(32, 32, 32, 0.05);
}

.legal-eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 12px;
  font-weight: 700;
  color: var(--bsp);
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
  background: var(--bsp);
  margin: 14px 0 16px;
}

.legal-meta {
  color: var(--muted-foreground);
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
  color: #575757;
  margin: 0 0 18px;
}

.legal-body .legal-intro {
  font-size: 16px;
  color: #353535;
}

.legal-body strong {
  color: var(--foreground);
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
  background: var(--bsp);
}

.legal-body ul {
  margin: 0 0 18px;
  padding: 0;
  list-style: none;
}

.legal-body ul li {
  font-size: 15.5px;
  line-height: 1.75;
  color: #575757;
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
  background: var(--bsp);
}

.legal-body ul li:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
}

.legal-body a {
  color: var(--bsp-link);
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid rgba(31, 138, 112, 0.35);
}

.legal-body a:hover { color: var(--bsp-link-hover); }

.legal-copy {
  margin: 44px 0 0;
  padding-top: 20px;
  border-top: 1px solid #ececec;
  color: var(--muted-foreground);
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
