const { chromium } = require('/Users/draxbarroga/.screencheck/node_modules/playwright');
const path = require('path');
const FILE = 'file://' + path.resolve(__dirname, 'msg_variants', 'index.html');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1440, height: 2200 });
  await page.goto(FILE, { waitUntil: 'networkidle' });
  await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(__dirname, 'msg_variants', 'variants.png'), fullPage: true });
  // per-variant crops for quick reading
  const boxes = await page.$$eval('.variant', els => els.map(e => {
    const r = e.getBoundingClientRect();
    return { x: r.x, y: r.y, width: r.width, height: r.height };
  }));
  const labels = await page.$$eval('.vhead .opt', els => els.map(e => e.textContent.trim()));
  for (let i = 0; i < boxes.length; i++) {
    await page.screenshot({ path: path.join(__dirname, 'msg_variants', `v${labels[i]||i}.png`), clip: boxes[i] });
  }
  const tableBox = await page.$eval('.tablewrap', e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; });
  await page.screenshot({ path: path.join(__dirname, 'msg_variants', 'table.png'), clip: tableBox });
  await browser.close();
  console.log('done', JSON.stringify(boxes.map(b => [Math.round(b.x), Math.round(b.y), Math.round(b.width), Math.round(b.height)])));
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
