const path = require('path'), fs = require('fs')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const T = fs.readFileSync(process.env.OA_TOKEN_FILE || require('path').join(__dirname, '..', '..', '.runtime', 'admin_token.txt'), 'utf8').trim()
const R = []; const ck = (n, ok, d = '') => { R.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }

;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 950 }, deviceScaleFactor: 2 })).newPage()
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

  // ---------- 菜单 ----------
  await p.goto(BASE + '/hr/position', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  const menuText = await p.locator('.oa-sidebar__menu').innerText()
  ck('侧边栏出现「人力资源」目录', menuText.includes('人力资源'), menuText.replace(/\s+/g, ' ').slice(0, 80))
  ck('目录下含 4 个子菜单',
    ['员工档案', '岗位管理', '考勤管理', '我的考勤'].every(n => menuText.includes(n)), '')

  // ---------- 岗位管理 ----------
  ck('岗位页 URL 正确', p.url().includes('/hr/position'), p.url().replace(BASE, ''))
  ck('岗位页渲染工具栏按钮', (await p.locator('button:has-text("新增岗位")').count()) >= 1)
  ck('岗位页表格可见', (await p.locator('.el-table').count()) >= 1)
  await p.locator('button:has-text("新增岗位")').first().click()
  await p.waitForTimeout(500)
  const dlg = p.locator('.el-dialog:has-text("新增岗位")')
  ck('新增岗位弹窗打开', await dlg.isVisible())
  ck('弹窗含编码/名称/部门字段',
    (await dlg.innerText()).includes('岗位编码') && (await dlg.innerText()).includes('所属部门'))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/hr_position_dialog.png' })
  await dlg.locator('.el-dialog__footer .el-button').first().click()
  await p.waitForTimeout(400)

  // ---------- 员工档案 ----------
  await p.goto(BASE + '/hr/employee', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1600)
  const rows = p.locator('.el-table__body tr.el-table__row')
  const rowCount = await rows.count()
  ck('花名册有数据', rowCount > 0, String(rowCount))
  const headers = (await p.locator('.el-table__header th').allInnerTexts()).map(s => s.trim())
  ck('表头含工号/姓名/任职状态/司龄',
    ['工号', '姓名', '任职状态', '司龄'].every(h => headers.some(x => x.includes(h))), headers.join('|'))
  const mobileCell = await rows.first().locator('td').nth(8).innerText()
  ck('列表手机号已脱敏', mobileCell.includes('****'), mobileCell)

  await p.locator('.el-table__body tr.el-table__row').first().locator('button').first().click()
  await p.waitForTimeout(1400)
  const drawer = p.locator('.el-drawer:has-text("的档案")')
  ck('档案抽屉打开', await drawer.isVisible())
  const tabs = (await drawer.locator('.el-tabs__item').allInnerTexts()).map(s => s.trim())
  ck('档案含基本信息/合同/证照/异动四个页签',
    tabs.length === 4 && tabs[0].includes('基本信息') && tabs[1].includes('合同')
    && tabs[2].includes('证照') && tabs[3].includes('异动'), tabs.join('|'))
  ck('HR 视角不显示敏感脱敏提示',
    (await drawer.locator('.el-alert:has-text("已脱敏")').count()) === 0)
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/hr_employee_drawer.png' })
  await p.locator('.el-drawer__close-btn').click()
  await p.waitForTimeout(400)

  // ---------- 考勤管理 ----------
  await p.goto(BASE + '/hr/attendance', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1600)
  const attTabs = (await p.locator('.el-tabs__item').allInnerTexts()).map(s => s.trim())
  ck('考勤页含明细/汇总/班次/排班页签',
    ['考勤明细', '月度汇总', '班次', '排班'].every(t => attTabs.includes(t)), attTabs.join('|'))
  ck('考勤明细表格渲染', (await p.locator('.el-table').count()) >= 1)
  await p.locator('.el-tabs__item:has-text("班次")').click()
  await p.waitForTimeout(600)
  ck('班次页签有新增按钮', (await p.locator('button:has-text("新增班次")').count()) >= 1)
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/hr_attendance_shift.png' })

  // ---------- 我的考勤 ----------
  await p.goto(BASE + '/hr/my-attendance', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1600)
  const bodyText = await p.locator('body').innerText()
  ck('我的考勤展示今日卡片', bodyText.includes('上班打卡') && bodyText.includes('下班打卡'))
  ck('admin 未排班时提示无需打卡', bodyText.includes('未排班'), bodyText.replace(/\s+/g, ' ').slice(0, 120))
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/hr_my_attendance.png' })

  ck('无运行时错误', errs.filter(e => !/setAllRowsExpansion|401 \(Unauthorized\)/.test(e)).length === 0,
    errs.slice(0, 2).join(' || '))
  await b.close()
  const f = R.filter(x => !x).length
  console.log(`\nSUMMARY: ${R.length - f}/${R.length}`)
})()
