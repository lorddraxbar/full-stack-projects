const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  const API = 'http://localhost:8080';
  await page.addInitScript(async ([email, pw, apiBase]) => {
    if (localStorage.getItem('accessToken')) return;
    const login = await fetch(apiBase + '/api/v1/auth/login', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({email,password:pw}) });
    const d = await login.json();
    if (!login.ok || !d.accessToken) { window.__seedFailed = true; return; }
    localStorage.setItem('accessToken', d.accessToken);
    localStorage.setItem('refreshToken', d.refreshToken || '');
    localStorage.setItem('userRole', d.user?.role || 'ADMIN');
    localStorage.setItem('userName', d.user?.fullName || 'Admin');
    if (d.user?.id) localStorage.setItem('userId', String(d.user.id));
  }, ['jayson@secphils.com', Buffer.from('cGFzc3dvcmQxMjM=','base64').toString('utf8'), API]);
  await page.goto('http://localhost:3000/auth/login', { waitUntil: 'domcontentloaded' });
  for (let i=0;i<40;i++){ if(await page.evaluate(()=>!!localStorage.getItem('accessToken'))) break; await page.waitForTimeout(250); }
  await page.goto('http://localhost:3000/projects', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);

  // First card: check the three zones don't horizontally overlap (desktop = 3 cols)
  const g = await page.evaluate(() => {
    const c = [...document.querySelectorAll('div')].find(d => d.className && /rounded-xl/.test(d.className) && d.querySelector('i.fa-clipboard-list'));
    if (!c) return null;
    const r = el => { const b = el.getBoundingClientRect(); return { x:Math.round(b.x), right:Math.round(b.right), y:Math.round(b.y), bottom:Math.round(b.bottom) }; };
    // icon tile, middle content (div with h3), right column (div containing badge)
    const icon = c.querySelector('i.fa-clipboard-list').closest('div');
    const name = c.querySelector('h3');
    const badge = [...c.querySelectorAll('span')].find(s=>/In Progress|Not Started|Completed|Archived|On Hold/.test(s.textContent));
    const right = badge ? badge.closest('div[class*="col-start-2"], div[class*="col-start-3"]') : null;
    const mid = c.querySelector('div[class*="row-start-1"][class*="min-w-0"]') || (name ? name.parentElement : null);
    return { card:r(c), icon:r(icon), mid: mid?r(mid):null, right: right?r(right):null };
  });
  console.log(JSON.stringify(g));
  if (g && g.right) {
    console.log('mid.right', g.mid.right, '< right.x', g.right.x, '=>', g.mid.right <= g.right.x ? 'NO OVERLAP ✓' : 'OVERLAP ✗');
  }
  await browser.close();
})();
