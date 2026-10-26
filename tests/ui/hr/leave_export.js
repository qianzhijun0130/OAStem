const path = require('path'), fs = require('fs')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const R = []; const ck = (n, ok, d = '') => { R.push([n, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }
;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 950 }, acceptDownloads: true })
  const p = await ctx.newPage()
  const errs = []; p.on('pageerror', e => errs.push(e.message))
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  const tok = await p.evaluate(async () => {
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'Admin@123456' }) })
    return (await r.json()).data.token
  })
  await p.evaluate(t => localStorage.setItem('oa_token', t), tok)
  await p.goto(BASE + '/hr/leave', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  await p.locator('.el-tabs__item:has-text("请假单")').first().click()
  await p.waitForTimeout(1200)
  const btn = p.locator('button:has-text("导出")').first()
  ck('请假单页签存在导出按钮', (await btn.count()) >= 1)
  const waiter = p.waitForEvent('download', { timeout: 25000 }).then(d => d).catch(() => null)
  await btn.click()
  const dl = await waiter
  if (!dl) { ck('请假导出触发下载', false, '无 download 事件'); }
  else {
    const f = await dl.path(); const size = f && fs.existsSync(f) ? fs.statSync(f).size : 0
    ck('请假导出触发下载', true, dl.suggestedFilename())
    ck('请假导出为 xlsx 且非空', dl.suggestedFilename().endsWith('.xlsx') && size > 500, `size=${size}`)
  }
  // 假别政策页签渲染
  await p.locator('.el-tabs__item:has-text("假别政策")').first().click()
  await p.waitForTimeout(1200)
  const pane = p.locator('.el-tab-pane:visible')
  ck('假别政策页签有内容', (await pane.locator('.el-table').count()) >= 1 || (await pane.innerText()).includes('法定天数'), '')
  ck('页面无 JS 运行期错误', errs.filter(e => !/favicon|ResizeObserver/.test(e)).length === 0, errs.slice(0, 2).join(';'))
  await b.close()
  const fail = R.filter(([, ok]) => !ok)
  console.log(`\nSUMMARY: ${R.length - fail.length}/${R.length} 通过`)
  process.exit(fail.length ? 1 : 0)
})().catch(e => { console.error('FATAL', e); process.exit(2) })
