const path = require('path')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const R = []; const ck = (n, ok, d = '') => { R.push([n, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }
;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1600, height: 950 } })).newPage()
  const errs = []; p.on('pageerror', e => errs.push(e.message))
  p.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text()) })
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  const tok = await p.evaluate(async () => {
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'Admin@123456' }) })
    return (await r.json()).data.token
  })
  await p.evaluate(t => localStorage.setItem('oa_token', t), tok)
  await p.goto(BASE + '/hr/salary', { waitUntil: 'networkidle' })
  await p.waitForTimeout(2500)
  const heads = (await p.locator('.el-table__header th').allInnerTexts()).map(s => s.trim()).filter(Boolean)
  ck('核算表格出现「折算系数」列', heads.includes('折算系数'), `heads=${heads.join('|')}`)
  ck('核算表格出现「折算后标准工资」列', heads.includes('折算后标准工资'))
  const idx1 = heads.indexOf('折算系数'), idx2 = heads.indexOf('折算后标准工资'), idx3 = heads.indexOf('应发')
  ck('两列位于「部门」与「应发」之间', idx1 > 0 && idx2 === idx1 + 1 && idx3 === idx2 + 1,
     `cols: 折算系数@${idx1} 折算后@${idx2} 应发@${idx3}`)
  ck('页面无 JS 运行期错误', errs.filter(e => !/favicon|ResizeObserver/.test(e)).length === 0, errs.slice(0, 2).join(';'))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/salary_proration_cols.png', fullPage: false })
  await b.close()
  const fail = R.filter(([, ok]) => !ok)
  console.log(`\nSUMMARY: ${R.length - fail.length}/${R.length} 通过`)
  process.exit(fail.length ? 1 : 0)
})().catch(e => { console.error('FATAL', e); process.exit(2) })
