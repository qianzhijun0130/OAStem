const path = require('path'), fs = require('fs')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const T = fs.readFileSync(process.env.OA_TOKEN_FILE || require('path').join(__dirname, '..', '..', '.runtime', 'admin_token.txt'), 'utf8').trim()
const R = []; const ck = (n, ok, d = '') => { R.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }

const CASES = [
  ['/dashboard', ['工作台']],
  ['/workflow/apply', ['工作台', '发起流程']],
  ['/workflow/todo', ['工作台', '任务中心', '待办任务']],
  ['/system/user', ['工作台', '系统管理', '用户管理']],
  ['/system/form', ['工作台', '流程管理', '表单设计']],
  ['/system/form/designer', ['工作台', '流程管理', '表单设计']],
  ['/workflow/apply/leave_apply', ['工作台', '发起流程', '填写申请']],
  ['/profile', ['工作台', '个人中心']],
]

;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  const errs = []
  p.on('pageerror', e => errs.push(e.message))
  p.on('console', m => { if (m.type() === 'error') errs.push(m.text()) })

  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await p.evaluate(t => localStorage.setItem('oa_token', t), T)

  for (const [route, expect] of CASES) {
    await p.goto(BASE + route, { waitUntil: 'networkidle' })
    await p.waitForTimeout(1200)
    const got = (await p.locator('.oa-navbar__crumb-item').allInnerTexts())
      .map(s => s.trim()).filter(Boolean)
    ck(`面包屑 ${route}`, JSON.stringify(got) === JSON.stringify(expect), `got=${got.join(' > ')}`)
  }

  // ---------- 可点性 ----------
  await p.goto(BASE + '/system/user', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1200)
  const items = p.locator('.oa-navbar__crumb-item')
  ck('第 1 项「工作台」可点击（a.oa-navbar__crumb-link）',
    (await items.nth(0).locator('a.oa-navbar__crumb-link').count()) === 1)
  ck('中间目录「系统管理」不可点击（纯文本）',
    (await items.nth(1).locator('a').count()) === 0 &&
    (await items.nth(1).locator('.oa-navbar__crumb-text:not(.is-current)').count()) === 1)
  ck('末项「用户管理」不可点击且标记为当前页',
    (await items.nth(2).locator('a').count()) === 0 &&
    (await p.locator('.oa-navbar__crumb-text.is-current').innerText()).trim() === '用户管理')

  // 点工作台 -> 回工作台
  await p.locator('.oa-navbar__crumb-link').first().click()
  await p.waitForTimeout(1200)
  ck('点击「工作台」跳转到 /dashboard', p.url().endsWith('/dashboard'), `url=${p.url().replace(BASE, '')}`)
  const dashCrumbs = (await p.locator('.oa-navbar__crumb-item').allInnerTexts()).map(s => s.trim())
  ck('工作台页面只显示一级面包屑', dashCrumbs.length === 1 && dashCrumbs[0] === '工作台', `got=${dashCrumbs.join('>')}`)

  // ---------- 右侧用户区 ----------
  await p.goto(BASE + '/system/user', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1000)
  ck('右侧用户胶囊含头像与姓名',
    (await p.locator('.oa-navbar__user .oa-navbar__avatar').count()) === 1 &&
    (await p.locator('.oa-navbar__user .oa-navbar__name').innerText()).trim().length > 0)
  await p.locator('.oa-navbar__user').click()
  await p.waitForTimeout(500)
  const ddItems = (await p.locator('.el-dropdown-menu__item').allInnerTexts()).map(s => s.trim())
  ck('点击用户胶囊弹出「个人中心/退出登录」',
    ddItems.some(t => t.includes('个人中心')) && ddItems.some(t => t.includes('退出登录')),
    `got=${ddItems.join('|')}`)
  ck('展开时箭头翻转（is-open）',
    (await p.locator('.oa-navbar__user.is-open').count()) === 1)
  await p.keyboard.press('Escape')
  await p.waitForTimeout(300)

  // ---------- 窄屏不撑破 Header ----------
  await p.setViewportSize({ width: 1024, height: 800 })
  await p.goto(BASE + '/system/user', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1000)
  const overflow = await p.evaluate(() => {
    const el = document.querySelector('.oa-navbar')
    return el ? el.scrollWidth - el.clientWidth : -1
  })
  ck('窄屏（1024）Header 无横向溢出', overflow <= 1, `overflow=${overflow}`)

  await p.setViewportSize({ width: 1440, height: 900 })
  await p.goto(BASE + '/system/user', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1000)
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/breadcrumb.png' })
  await p.goto(BASE + '/workflow/todo', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1000)
  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/breadcrumb_3level.png' })

  const bad = errs.filter(e => /Cannot read|TypeError|Failed to resolve|500/.test(e) && !/setAllRowsExpansion/.test(e))
  ck('无运行时错误', bad.length === 0, bad.slice(0, 1).join(''))

  await b.close()
  const f = R.filter(x => !x).length
  console.log(`\nSUMMARY: ${R.length - f}/${R.length} 通过`)
  process.exit(f ? 1 : 0)
})().catch(e => { console.error('SCRIPT_ERROR', e); process.exit(2) })
