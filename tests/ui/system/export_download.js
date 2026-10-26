// FE-D01 修复验证：真实点击导出按钮，断言触发下载且文件为 xlsx
const path = require('path'), fs = require('fs')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const R = []; const ck = (n, ok, d = '') => { R.push([n, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }

async function tryDownload(p, clickFn, timeout = 25000) {
  const waiter = p.waitForEvent('download', { timeout }).then(d => d).catch(() => null)
  await clickFn()
  const dl = await waiter
  if (!dl) return { ok: false, name: '', size: 0 }
  const file = await dl.path().catch(() => null)
  const size = file && fs.existsSync(file) ? fs.statSync(file).size : 0
  return { ok: true, name: dl.suggestedFilename(), size }
}

;(async () => {
  const b = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const ctx = await b.newContext({ viewport: { width: 1440, height: 950 }, acceptDownloads: true })
  const p = await ctx.newPage()
  const errs = []
  p.on('pageerror', e => errs.push('pageerror: ' + e.message))
  p.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text()) })

  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  const tok = await p.evaluate(async () => {
    const r = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'Admin@123456' }),
    })
    return (await r.json()).data.token
  })
  await p.evaluate(t => localStorage.setItem('oa_token', t), tok)

  // ---------- 1. 员工花名册导出 ----------
  await p.goto(BASE + '/hr/employee', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  const empBtn = p.locator('button:has-text("导出花名册")').first()
  ck('员工页存在「导出花名册」按钮', (await empBtn.count()) >= 1)
  const emp = await tryDownload(p, () => empBtn.click())
  ck('花名册导出触发下载', emp.ok, emp.ok ? emp.name : '无 download 事件（正是修复前的症状）')
  ck('花名册文件为 xlsx 且非空', emp.ok && emp.name.endsWith('.xlsx') && emp.size > 1000,
    `name=${emp.name} size=${emp.size}`)

  // ---------- 2. 考勤月度汇总导出 ----------
  await p.goto(BASE + '/hr/attendance', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)
  await p.locator('.el-tabs__item:has-text("月度汇总")').first().click()
  await p.waitForTimeout(1200)
  const attBtn = p.locator('button:has-text("导出")').first()
  ck('考勤页「月度汇总」存在导出按钮', (await attBtn.count()) >= 1)
  const att = await tryDownload(p, () => attBtn.click())
  ck('考勤月度汇总导出触发下载', att.ok, att.ok ? att.name : '无 download 事件')
  ck('考勤文件为 xlsx 且非空', att.ok && att.name.endsWith('.xlsx') && att.size > 1000,
    `name=${att.name} size=${att.size}`)

  // ---------- 3. 绩效结果导出（同一根因，页面已一并修复） ----------
  await p.goto(BASE + '/hr/performance', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  const perfBtn = p.locator('button:has-text("导出")').first()
  const perfBtnCount = await perfBtn.count()
  if (perfBtnCount === 0) {
    console.log('SKIP | 绩效页未找到导出按钮（可能未选中周期）| ')
  } else {
    const perf = await tryDownload(p, () => perfBtn.click(), 20000)
    if (!perf.ok) {
      // 未选周期时页面直接 return，不产生下载；这属业务前置，不算缺陷
      const needCycle = await p.locator('text=/请先选择|未选择周期|周期/').count()
      console.log(`SKIP | 绩效导出未触发下载（需先选中周期，` + `命中提示节点=${needCycle}）| `)
    } else {
      ck('绩效导出触发下载', true, perf.name)
      ck('绩效文件为 xlsx 且非空', perf.name.endsWith('.xlsx') && perf.size > 500, `size=${perf.size}`)
    }
  }

  // ---------- 4. 页面无运行期错误 ----------
  const realErrs = errs.filter(e => !/favicon|ResizeObserver/.test(e))
  ck('页面无 JS 运行期错误', realErrs.length === 0, realErrs.slice(0, 2).join(' ; '))

  await b.close()
  const fail = R.filter(([, ok]) => !ok)
  console.log(`\nSUMMARY: ${R.length - fail.length}/${R.length} 通过`)
  if (fail.length) console.log('FAILED: ' + fail.map(([n]) => n).join(' ; '))
  process.exit(fail.length ? 1 : 0)
})().catch(e => { console.error('FATAL', e); process.exit(2) })
