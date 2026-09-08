// Live verification of the new one-card-per-row Projects list.
// Seeds a real admin session (localStorage) then navigates to /projects.
const fs = require('fs');
const { chromium } = require('playwright');

const BASE = 'http://localhost:3000';
const API = 'http://localhost:8080';
const ADMIN_EMAIL = 'jayson@secphils.com';
const ADMIN_PW = Buffer.from('cGFzc3dvcmQxMjM=', 'base64').toString('utf8');
const OUT = '/Users/draxbarroga/Git/full-stack-projects/secphils/sketches/e2e-shots';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch();

  for (const vw of [1280, 390]) {
    const ctx = await browser.newContext({ viewport: { width: vw, height: 900 }, deviceScaleFactor: 2 });
    const page = await ctx.newPage();
    const errs = [];
    page.on('pageerror', e => errs.push('PAGEERROR: ' + e.message));
    page.on('console', m => { if (m.type() === 'error') errs.push('CONSOLE: ' + m.text().slice(0, 160)); });
    page.on('response', r => { if (r.status() >= 500 && r.url().includes('/api/')) errs.push(`API ${r.status()} ${r.url().replace(BASE, '')}`); });

    await page.addInitScript(async ([email, pw, apiBase]) => {
      if (localStorage.getItem('accessToken')) return;
      const login = await fetch(apiBase + '/api/v1/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password: pw }),
      });
      const data = await login.json();
      if (!login.ok || !data.accessToken) { window.__seedFailed = true; return; }
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken || '');
      localStorage.setItem('userRole', (data.user && data.user.role) || 'ADMIN');
      localStorage.setItem('userName', (data.user && data.user.fullName) || 'Admin');
      if (data.user && data.user.id) localStorage.setItem('userId', String(data.user.id));
    }, [ADMIN_EMAIL, ADMIN_PW, API]);

    await page.goto(BASE + '/auth/login', { waitUntil: 'domcontentloaded' });
    let seeded = false;
    for (let i = 0; i < 40; i++) {
      if (await page.evaluate(() => !!localStorage.getItem('accessToken'))) { seeded = true; break; }
      await page.waitForTimeout(250);
    }
    const seedFailed = await page.evaluate(() => !!window.__seedFailed);
    console.log(`[${vw}] seeded=${seeded} failed=${seedFailed}`);

    await page.goto(BASE + '/projects', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);

    const info = await page.evaluate(() => {
      const cards = [...document.querySelectorAll('div')].filter(d =>
        d.className && /rounded-xl/.test(d.className) && /border-gray-200/.test(d.className) &&
        d.querySelector('h3') && d.querySelector('i.fa-clipboard-list'));
      const c0 = cards[0];
      return {
        cardCount: cards.length,
        firstName: c0 ? c0.querySelector('h3')?.textContent?.trim().slice(0, 45) : null,
        iconTile: c0 ? !!c0.querySelector('i.fa-clipboard-list') : false,
        metaDots: c0 ? c0.querySelectorAll('span').length : -1,
        hasBadge: c0 ? !![...c0.querySelectorAll('span')].find(s => /In Progress|Not Started|Completed|Archived|On Hold/.test(s.textContent)) : false,
        hasCost: c0 ? /₱|—/.test(c0.textContent) : false,
        hasCreated: c0 ? /Created/.test(c0.textContent) : false,
        hasCompleted: c0 ? /Completed/.test(c0.textContent) : false,
        hasUpdate: c0 ? (c0.textContent.includes('ago') || c0.textContent.includes('No updates yet')) : false,
        title: document.title,
      };
    });
    console.log(`[${vw}]`, JSON.stringify(info));
    console.log(`[${vw}] errors:`, errs.length ? errs.join(' | ') : 'none');

    await page.screenshot({ path: `${OUT}/live-projects-${vw}.png`, fullPage: false });
    await ctx.close();
  }
  await browser.close();
  console.log('done');
})();
