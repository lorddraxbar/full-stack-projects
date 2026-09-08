const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(__dirname, 'rows-a-single', 'index.html'), { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(300);
  const g = await page.evaluate(() => {
    const c = document.querySelector('.pcard');
    const r = el => { const b = el.getBoundingClientRect(); return { x:Math.round(b.x), y:Math.round(b.y), w:Math.round(b.width), right:Math.round(b.right), bottom:Math.round(b.bottom) }; };
    return { card:r(c), ic:r(c.querySelector('.p-ic')), name:r(c.querySelector('.p-name')), meta:r(c.querySelector('.p-meta')), update:r(c.querySelector('.p-update')), right:r(c.querySelector('.p-right')), dates:r(c.querySelector('.p-dates')) };
  });
  console.log(JSON.stringify(g));
  await browser.close();
})();
