const path = require('path')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const R = []; const ck = (n, ok, d = '') => { R.push([n, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }
;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1700, height: 950 } })).newPage()
  const errs = []; p.on('pageerror', e => errs.push(e.message))
  await p.goto('http://127.0.0.1:3000/login', { waitUntil: 'networkidle' })
  const tok = await p.evaluate(async () => (await (await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'Admin@123456' }) })).json()).data.token)
  await p.evaluate(t => localStorage.setItem('oa_token', t), tok)
  await p.goto('http://127.0.0.1:3000/hr/salary', { waitUntil: 'networkidle' })
  await p.waitForTimeout(2000)
  const tabs = await p.locator('.el-tabs__item').allInnerTexts()
  console.log('tabs=', tabs.map(s => s.trim()).join('|'))
  // 月度核算需要选月份才会加载数据：填入 2026-10 并点查询
  const monthInput = p.locator('.el-date-editor input').first()
  if (await monthInput.count()) { await monthInput.fill('2026-10'); await monthInput.press('Enter') }
  await p.waitForTimeout(600)
  const qBtn = p.locator('button:has-text("查询")').first()
  if (await qBtn.count()) { await qBtn.click(); await p.waitForTimeout(2000) }
  const tab = p.locator('.el-tabs__item', { hasText: '核算' }).first()
  if (await tab.count()) { await tab.click(); await p.waitForTimeout(2500) }
  const rows = await p.locator('.salary__record-table .el-table__body tr').count()
  ck('核算表格有数据行', rows > 0, `rows=${rows}`)
  const tag = p.locator('.salary__prior')
  ck('出现「跨单位」标记', (await tag.count()) >= 1, `count=${await tag.count()}`)
  ck('标记文案为「跨单位」', (await tag.count()) > 0 && (await tag.first().innerText()).trim() === '跨单位')
  ck('页面无运行期错误', errs.filter(e => !/favicon|ResizeObserver/.test(e)).length === 0, errs.slice(0, 2).join(';'))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/salary_prior_tag.png' })
  await b.close()
  const fail = R.filter(([, ok]) => !ok)
  console.log(`\nSUMMARY: ${R.length - fail.length}/${R.length} 通过`)
  process.exit(fail.length ? 1 : 0)
})().catch(e => { console.error('FATAL', e.message); process.exit(2) })
