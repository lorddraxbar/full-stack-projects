const { chromium } = require('/Users/draxbarroga/.screencheck/node_modules/playwright');
const path = require('path');
const FILE = 'file://' + path.resolve(__dirname, 'msg_variants', 'index.html');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1440, height: 2200 });
  await page.goto(FILE, { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);

  // per-variant: assert key strings + composer banner state
  const variants = await page.$$eval('.variant', (els) => els.map((v) => {
    const opt = v.querySelector('.opt')?.textContent.trim();
    const title = v.querySelector('.vtitle')?.textContent.trim();
    const banner = v.querySelector('.cbanner')?.textContent.trim();
    const btn = v.querySelector('.cfoot .btn')?.textContent.trim();
    const seg = [...v.querySelectorAll('.seg button')].map(b => b.textContent.trim());
    const trackOn = v.querySelector('.track.on') ? true : false;
    const rec = !!v.querySelector('.rec');
    return { opt, title, banner, btn, seg, trackOn, rec };
  }));
  for (const v of variants) console.log(JSON.stringify(v));

  // internal badge in thread previews (all 4 should show it once each in preview)
  const badges = await page.$$eval('.thread .ibadge', els => els.length);
  console.log('thread internal badges:', badges);

  // table rows
  const rows = await page.$$eval('.tablewrap tbody tr', trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.trim().slice(0, 40))));
  rows.forEach(r => console.log('ROW:', r.join(' | ')));
  await browser.close();
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
