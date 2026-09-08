const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  const out = path.join(__dirname, 'e2e-shots');
  const fs = require('fs');
  fs.mkdirSync(out, { recursive: true });

  for (const v of ['rows-a-grid', 'rows-b-table', 'rows-c-list']) {
    await page.goto('file://' + path.join(__dirname, v, 'index.html'), { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(600);
    const errs = await page.evaluate(() => document.body.innerText.slice(0, 60));
    const el = await page.$('.card-shell');
    if (!el) { console.log(v, 'NO card-shell', errs); await page.screenshot({ path: `${out}/${v}.png` }); continue; }
    await el.screenshot({ path: `${out}/${v}.png` });
    console.log(v, 'ok, shell captured');
  }
  await browser.close();
  console.log('done');
})();
