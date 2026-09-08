const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const browser = await chromium.launch();
  const out = path.join(__dirname, 'e2e-shots');
  fs.mkdirSync(out, { recursive: true });

  // Desktop 1280 + mobile 390 to check the single-column reflow
  for (const vw of [1280, 390]) {
    const page = await browser.newPage({ viewport: { width: vw, height: 900 }, deviceScaleFactor: 2 });
    await page.goto('file://' + path.join(__dirname, 'rows-a-single', 'index.html'), { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(500);
    const el = await page.$('.shell');
    if (!el) { console.log(vw, 'NO shell'); await page.screenshot({ path: `${out}/rows-a-single-${vw}.png` }); await page.close(); continue; }
    await el.screenshot({ path: `${out}/rows-a-single-${vw}.png` });
    console.log(vw, 'captured');
    await page.close();
  }
  await browser.close();
  console.log('done');
})();
