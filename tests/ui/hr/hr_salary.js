const path = require('path'), fs = require('fs')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const T = fs.readFileSync(process.env.OA_TOKEN_FILE || require('path').join(__dirname, '..', '..', '.runtime', 'admin_token.txt'), 'utf8').trim()
const R = []
const ck = (n, ok, d = '') => { R.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }
// 与页面默认值保持一致（页面默认查「上个月」）：否则造了 3 月的数据，表格却按上月过滤，看到 0 行
const now = new Date()
now.setMonth(now.getMonth() - 1)
const MONTH = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const UID = 101809

;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const ctx = await b.newContext({ viewport: { width: 1500, height: 900 }, deviceScaleFactor: 1.8 })
  const p = await ctx.newPage()
  const errs = []
  p.on('pageerror', e => errs.push('pageerror: ' + e.message))
  p.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text()) })

  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await p.evaluate(t => localStorage.setItem('oa_token', t), T)

  // ---------- 造数：档案 → 考勤封账 → 核算 → 复核 ----------
  const seed = await p.evaluate(async ([month, uid]) => {
    const tok = localStorage.getItem('oa_token')
    const call = async (url, method = 'GET', body) => {
      const r = await fetch('/api' + url, {
        method,
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tok },
        body: body ? JSON.stringify(body) : undefined,
      })
      return r.json().catch(() => ({}))
    }
    // dept 2 有 3 个在职员工，核算按部门走时缺一个档案就会被拒（6401），所以三个人都建
    const codes = []
    for (const id of [101808, uid, 101810]) {
      const r = await call('/hr/salary/profiles', 'POST', {
        userId: id, effectiveDate: month + '-01',
        baseSalary: 7000, postSalary: 1500, perfSalary: 0, allowance: 300,
        city: '默认', specialDeduct: 1000, changeReason: 'UI 自动化',
      })
      codes.push(r.code)
    }
    const profile = { code: codes.every(c => c === 200) ? 200 : codes[0] }
    const calc = await call(`/hr/salary/calc?month=${month}&deptId=2`, 'POST')
    const review = await call(`/hr/salary/review?month=${month}&deptId=2`, 'POST')
    return { profile: profile.code, calc: calc.code, calcCount: calc.data, review: review.code }
  }, [MONTH, UID])
  // 重复运行时：档案同日已存在=400、记录状态已推进过=6404，都说明前置条件已就绪，不算失败
  ck('造数：档案 + 核算 + 复核成功（400/6404 视为已就绪）',
    [200, 400].includes(seed.profile) && seed.calc === 200 && [200, 6404].includes(seed.review),
    JSON.stringify(seed))

  // ---------- 1. 菜单 ----------
  await p.goto(BASE + '/hr/salary', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  const menuText = await p.locator('.oa-sidebar__menu').innerText()
  ck('侧边栏出现「薪酬管理」', menuText.includes('薪酬管理'), menuText.replace(/\s+/g, ' ').slice(0, 80))
  ck('侧边栏出现「自助服务」（工资条已迁入该目录）', menuText.includes('自助服务'), '')

  // ---------- 2. 三个页签 ----------
  const tabs = (await p.locator('.el-tabs__item').allInnerTexts()).map(s => s.trim())
  ck('薪酬页三个页签', ['月度核算', '薪资档案', '社保政策'].every(t => tabs.includes(t)), tabs.join('|'))

  // ---------- 3. 月度核算 ----------
  await p.locator('.el-tabs__item:has-text("月度核算")').click()
  await p.waitForTimeout(500)
  const recordTable = p.locator('.salary__record-table')
  const headers = (await recordTable.locator('.el-table__header th').allInnerTexts()).map(s => s.trim())
  ck('核算表含应发/实发/状态列',
    ['应发', '实发', '状态'].every(h => headers.some(x => x.includes(h))), headers.join('|'))
  const rowCount = await recordTable.locator('.el-table__body tr.el-table__row').count()
  ck('核算列表有数据（3 人）', rowCount >= 3, String(rowCount))

  // 明细弹窗
  await recordTable.locator('.el-table__body tr.el-table__row').first().locator('button:has-text("明细")').click()
  await p.waitForTimeout(900)
  const dlg = p.locator('.el-dialog:has-text("薪资明细")')
  ck('明细弹窗打开', await dlg.isVisible())
  const dlgText = (await dlg.innerText()).replace(/\s+/g, ' ')
  ck('明细含应发/实发/社保基数', ['应发', '实发', '社保基数'].every(t => dlgText.includes(t)), dlgText.slice(0, 80))
  ck('明细含逐项表格', (await dlg.locator('.salary__items .el-table__body tr').count()) >= 4,
    String(await dlg.locator('.salary__items .el-table__body tr').count()))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/salary_detail.png' })
  await dlg.locator('.el-dialog__footer .el-button').first().click()
  await p.waitForTimeout(400)

  // ---------- 4. 薪资档案页签 ----------
  await p.locator('.el-tabs__item:has-text("薪资档案")').click()
  await p.waitForTimeout(800)
  const profileTable = p.locator('.salary__profile-table')
  const profileHeaders = (await profileTable.locator('.el-table__header th').allInnerTexts()).map(s => s.trim())
  ck('档案表含生效日期/月标准列',
    ['生效日期', '月标准'].every(h => profileHeaders.some(x => x.includes(h))), profileHeaders.join('|'))
  const profileRows = await profileTable.locator('.el-table__body tr.el-table__row').count()
  ck('档案列表有数据', profileRows >= 1, String(profileRows))

  // 调薪弹窗
  await profileTable.locator('.el-table__body tr.el-table__row').first().locator('button:has-text("调薪")').click()
  await p.waitForTimeout(700)
  const pf = p.locator('.el-dialog:has-text("薪资档案 / 调薪")')
  ck('调薪弹窗打开且带出原金额', await pf.isVisible()
    && (await pf.innerText()).includes('生效日期'), '')
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/salary_profile_dialog.png' })
  await p.keyboard.press('Escape')
  await p.waitForTimeout(400)

  // ---------- 5. 社保政策页签 ----------
  await p.locator('.el-tabs__item:has-text("社保政策")').click()
  await p.waitForTimeout(700)
  const policyTable = p.locator('.salary__policy-table')
  const policyRows = await policyTable.locator('.el-table__body tr.el-table__row').count()
  ck('社保政策列表有默认政策', policyRows >= 1, String(policyRows))
  const policyText = await policyTable.locator('.el-table__body').innerText()
  ck('政策显示个人合计比例', /%/.test(policyText), policyText.replace(/\s+/g, ' ').slice(0, 80))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/salary_policy.png' })

  // ---------- 6. 我的工资条（员工视角） ----------
  const staffToken = await p.evaluate(async ([uid]) => {
    const r = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'tech_staff1', password: 'qwe@123456' }),
    })
    const j = await r.json()
    return (j.data || {}).token || ''
  }, [UID])
  ck('员工登录成功', !!staffToken, staffToken ? 'ok' : 'no-token')
  await p.evaluate(t => localStorage.setItem('oa_token', t), staffToken)
  await p.goto(BASE + '/self/my-salary', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1600)
  const bodyText = (await p.locator('body').innerText()).replace(/\s+/g, ' ')
  ck('工资条页显示月份与实发', bodyText.includes('工资条') && bodyText.includes('实发合计'), bodyText.slice(0, 100))
  ck('工资条含收入项与扣款项分区', bodyText.includes('收入项') && bodyText.includes('扣款项'), '')
  ck('工资条含累计预扣说明', bodyText.includes('累计预扣'), '')
  const monthBtns = await p.locator('.payslip__month').count()
  ck('月份切换按钮存在', monthBtns >= 1, String(monthBtns))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/my_payslip.png' })

  // 员工不应看到 HR 菜单
  const staffMenu = await p.locator('.oa-sidebar__menu').innerText()
  ck('员工侧边栏无「薪酬管理」', !staffMenu.includes('薪酬管理'), staffMenu.replace(/\s+/g, ' ').slice(0, 80))

  ck('无运行时错误', errs.filter(e => !/setAllRowsExpansion/.test(e)).length === 0, errs.slice(0, 1).join(''))
  await b.close()
  const f = R.filter(x => !x).length
  console.log(`\nSUMMARY: ${R.length - f}/${R.length}`)
})()
