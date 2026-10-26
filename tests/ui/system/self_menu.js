const path = require('path')
const { chromium } = (() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE = 'http://127.0.0.1:3000'
const R = []; const ck = (n, ok, d = '') => { R.push([n, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }


async function openItem(p, groupTitle, itemName) {
  const sub = p.locator('.oa-sidebar__menu .el-sub-menu').filter({ has: p.locator(`.el-sub-menu__title:has-text("${groupTitle}")`) }).first()
  const opened = await sub.evaluate(el => el.classList.contains('is-opened')).catch(() => false)
  if (!opened) {
    await sub.locator('.el-sub-menu__title').first().click()
    await p.waitForTimeout(700)
  }
  const item = sub.locator(`.el-menu-item:has-text("${itemName}")`).first()
  await item.scrollIntoViewIfNeeded()
  await item.click()
}

async function loginCtx(b, username, password) {
  const ctx = await b.newContext({ viewport: { width: 1440, height: 1000 } })
  const p = await ctx.newPage()
  const errs = []; p.on('pageerror', e => errs.push(e.message))
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  const tok = await p.evaluate(async ([u, pw]) => {
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: pw }) })
    return (await r.json()).data.token
  }, [username, password])
  await p.evaluate(t => localStorage.setItem('oa_token', t), tok)
  return { p, errs }
}

;(async () => {
  const b = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })

  // ---------- 管理员 ----------
  const { p, errs } = await loginCtx(b, 'admin', 'Admin@123456')
  await p.goto(BASE + '/dashboard', { waitUntil: 'networkidle' })
  await p.waitForTimeout(2200)
  const menuText = await p.locator('.oa-sidebar__menu').innerText()
  ck('侧边栏出现一级「自助服务」', menuText.includes('自助服务'))
  ck('侧边栏仍有一级「人力资源」', menuText.includes('人力资源'))

  // 按分组校验归属：取每个一级目录下的子菜单文本
  const groups = await p.evaluate(() => {
    const out = {}
    document.querySelectorAll('.oa-sidebar__menu .el-sub-menu').forEach(sm => {
      const title = sm.querySelector('.el-sub-menu__title')?.innerText?.trim() || ''
      const items = Array.from(sm.querySelectorAll('.el-menu-item')).map(i => i.innerText.trim()).filter(Boolean)
      out[title] = items
    })
    return out
  })
  const hrItems = groups['人力资源'] || []
  const selfItems = groups['自助服务'] || []
  ck('「人力资源」不再包含我的考勤', !hrItems.includes('我的考勤'), `hr=[${hrItems.join(',')}]`)
  ck('「人力资源」不再包含我的工资条/绩效/培训',
    !hrItems.some(t => ['我的工资条', '我的绩效', '我的培训'].includes(t)))
  ck('「自助服务」包含 4 个自助页',
    ['我的考勤', '我的工资条', '我的绩效', '我的培训'].every(t => selfItems.includes(t)),
    `self=[${selfItems.join(',')}]`)

  // ---------- 跳转与渲染 ----------
  const targets = [
    ['我的考勤', '/self/my-attendance'],
    ['我的工资条', '/self/my-salary'],
    ['我的绩效', '/self/my-performance'],
    ['我的培训', '/self/my-training'],
  ]
  for (const [name, expectPath] of targets) {
    await openItem(p, '自助服务', name)
    await p.waitForTimeout(1800)
    ck(`点击「${name}」跳转 ${expectPath}`, p.url().includes(expectPath), p.url().replace(BASE, ''))
    const mainText = await p.locator('.oa-main, .el-main, main').first().innerText().catch(() => '')
    ck(`「${name}」页面有内容渲染`, mainText.trim().length > 20, `len=${mainText.trim().length}`)
  }
  ck('管理员视角无 JS 运行期错误', errs.filter(e => !/favicon|ResizeObserver/.test(e)).length === 0,
    errs.slice(0, 2).join(';'))

  // ---------- 普通员工 ----------
  const e2 = await loginCtx(b, 'tech_staff1', 'qwe@123456')
  await e2.p.goto(BASE + '/dashboard', { waitUntil: 'networkidle' })
  await e2.p.waitForTimeout(2200)
  const empGroups = await e2.p.evaluate(() => {
    const out = {}
    document.querySelectorAll('.oa-sidebar__menu .el-sub-menu').forEach(sm => {
      const title = sm.querySelector('.el-sub-menu__title')?.innerText?.trim() || ''
      out[title] = Array.from(sm.querySelectorAll('.el-menu-item')).map(i => i.innerText.trim()).filter(Boolean)
    })
    return out
  })
  const empSelf = empGroups['自助服务'] || []
  ck('普通员工可见「自助服务」且含 4 项',
    ['我的考勤', '我的工资条', '我的绩效', '我的培训'].every(t => empSelf.includes(t)),
    `self=[${empSelf.join(',')}]`)
  await openItem(e2.p, '自助服务', '我的考勤')
  await e2.p.waitForTimeout(1800)
  ck('普通员工可打开我的考勤', e2.p.url().includes('/self/my-attendance'), e2.p.url().replace(BASE, ''))
  ck('普通员工视角无 JS 运行期错误', e2.errs.filter(e => !/favicon|ResizeObserver/.test(e)).length === 0,
    e2.errs.slice(0, 2).join(';'))

  await p.screenshot({ path: require('path').join(__dirname, '..', '..', '.runtime') + '/self_menu_sidebar.png' })
  await b.close()
  const fail = R.filter(([, ok]) => !ok)
  console.log(`\nSUMMARY: ${R.length - fail.length}/${R.length} 通过`)
  if (fail.length) console.log('FAILED: ' + fail.map(([n]) => n).join(' ; '))
  process.exit(fail.length ? 1 : 0)
})().catch(e => { console.error('FATAL', e); process.exit(2) })
