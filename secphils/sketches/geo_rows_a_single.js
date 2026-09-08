const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 });
  const errs = [];
  page.on('pageerror', e => errs.push('PAGEERROR: ' + e.message));
  await page.goto('file://' + path.join(__dirname, 'rows-a-single', 'index.html'), { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(400);

  // For the first card, compare x-extents of the left content vs right block
  const geo = await page.evaluate(() => {
    const c = document.querySelector('.pcard');
    const r = el => { const b = el.getBoundingClientRect(); return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), right: Math.round(b.right), bottom: Math.round(b.bottom) }; };
    return {
      card: r(c),
      ic: r(c.querySelector('.p-ic')),
      name: r(c.querySelector('.p-name')),
      mid: r(c.querySelector('.p-mid')),
      right: r(c.querySelector('.p-right')),
      dates: r(c.querySelector('.p-dates')),
    };
  });
  console.log(JSON.stringify(geo, null, 1));
  const midRight = geo.mid.right, rightLeft = geo.right.x;
  console.log('mid.right =', midRight, ' right.x =', rightLeft, ' overlap?', midRight > rightLeft);
  // Check dates fit inside card
  console.log('dates.right =', geo.dates.right, ' card.right =', geo.card.right, ' fits?', geo.dates.right <= geo.card.right + 1);
  console.log('pageerrors:', errs.length ? errs : 'none');
  await browser.close();
})();
