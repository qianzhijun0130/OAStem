const path = require('path'), fs = require('fs')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const T = fs.readFileSync(process.env.OA_TOKEN_FILE || require('path').join(__dirname, '..', '..', '.runtime', 'admin_token.txt'), 'utf8').trim()
const R = []; const ck = (n, ok, d = '') => { R.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }

;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1500, height: 950 }, deviceScaleFactor: 2 })).newPage()
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
  await p.evaluate(t => localStorage.setItem('oa_token', t), tok || T)

  await p.goto(BASE + '/hr/leave', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)

  ck('假勤管理页可访问', p.url().includes('/hr/leave'), p.url().replace(BASE, ''))
  const menuText = await p.locator('.oa-sidebar__menu').innerText()
  ck('侧边栏出现「假勤管理」', menuText.includes('假勤管理'), '')

  const tabs = (await p.locator('.el-tabs__item').allInnerTexts()).map(s => s.trim())
  ck('含五个页签（额度/请假/加班/出差/补卡）',
    ['我的额度', '请假单', '加班', '出差', '补卡'].every(t => tabs.includes(t)), tabs.join('|'))

  // 我的额度
  const quotaRows = await p.locator('.el-table__body tr.el-table__row').count()
  ck('我的额度有数据（年假/调休）', quotaRows >= 2, String(quotaRows))
  const quotaText = await p.locator('.el-table').first().innerText()
  ck('额度表含「年假」与「可用」列', quotaText.includes('年假') && quotaText.includes('可用'), '')

  // 请假单
  await p.locator('.el-tabs__item:has-text("请假单")').click()
  await p.waitForTimeout(1200)
  ck('请假单表格渲染', (await p.locator('.el-table').count()) >= 1)
  const headers = (await p.locator('.el-table__header th').allInnerTexts()).map(s => s.trim())
  ck('请假表头含 单据编号/假别/天数/状态',
    ['单据编号', '假别', '天数', '状态'].every(h => headers.some(x => x.includes(h))), headers.join('|'))

  await p.locator('button:has-text("发起请假")').click()
  await p.waitForTimeout(500)
  const dlg = p.locator('.el-dialog:has-text("发起请假")')
  ck('发起请假弹窗打开', await dlg.isVisible())
  const dlgText = await dlg.innerText()
  ck('弹窗含假别/起止时间/天数/事由',
    ['假别', '开始时间', '结束时间', '请假天数', '事由'].every(t => dlgText.includes(t)), '')

  // 关闭弹窗（提交链路已由 verify_hr_leave.py 34/34 覆盖；UI 这里只验渲染与必填项）
  await dlg.locator('.el-dialog__footer .el-button').first().click()
  await p.waitForTimeout(400)
  ck('弹窗可正常关闭', !(await dlg.isVisible()), '')

  // 加班页签
  await p.locator('.el-tabs__item:has-text("加班")').click()
  await p.waitForTimeout(1000)
  ck('加班页签可切换', (await p.locator('.el-table').count()) >= 1)
  await p.locator('button:has-text("发起加班")').click()
  await p.waitForTimeout(500)
  const bizText = await p.locator('.el-dialog:visible').innerText()
  ck('加班弹窗含补偿方式（转调休/加班费）', bizText.includes('转调休'), '')
  await p.locator('.el-dialog:visible .el-dialog__footer .el-button').first().click()
  await p.waitForTimeout(300)

  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/hr_leave_page.png' })
  ck('无运行时错误', errs.filter(e => !/setAllRowsExpansion|401 \(Unauthorized\)/.test(e)).length === 0,
    errs.slice(0, 2).join(' || '))
  await b.close()
  const f = R.filter(x => !x).length
  console.log(`\nSUMMARY: ${R.length - f}/${R.length}`)
})()
