// BIZ-OPT-08 UI 回归：考勤机导入入口、漏卡清单、打卡来源标识。
//
// 数据由 seed_machine_fixture.py 造（当月 10-28 有完整考勤机打卡、10-29 只有上班卡），
// 跑完清掉——不依赖库里既有数据，避免像 hr_salary.js 那样被别的用例清空后判红。
const path = require('path')
const { execFileSync } = require('child_process')
const { launch, login, realErrors, createReport } = require('../lib/browser')

const BASE = 'http://127.0.0.1:3000'
const PY = 'C:/Users/qianz/.workbuddy/binaries/python/envs/default/Scripts/python.exe'
const SEED = path.join(__dirname, '..', '..', 'api', 'hr', 'seed_machine_fixture.py')
const { ck, summary } = createReport()

function seed(flag) {
  const args = [SEED]
  if (flag) args.push(flag)
  return execFileSync(PY, args, { encoding: 'utf8', timeout: 120000 }).trim()
}

;(async () => {
  const seedOut = seed()
  ck('夹具准备', seedOut.includes('imported=2'), seedOut)

  /* ---------- 1. 管理员视角 ---------- */

  const { browser, page, errors } = await launch({ width: 1500, height: 950 })
  const calls = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\/hr\/attendance/.test(url)) return
    let code = null
    try {
      code = (await res.json())?.code
    } catch {
      code = 'no-json'
    }
    calls.push({ url: url.replace(BASE, ''), status: res.status(), code })
  })
  const hit = (frag) => calls.filter((c) => c.url.includes(frag))
  const okHit = (frag) => hit(frag).some((c) => c.status === 200 && (c.code === 200 || c.code == null))

  await login(page, BASE)
  await page.goto(BASE + '/hr/attendance', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  ck('考勤页可访问', page.url().includes('/hr/attendance'), page.url().replace(BASE, ''))
  ck('首屏取明细', okHit('/hr/attendance/page'), JSON.stringify(hit('/hr/attendance/page').length))

  /* 导入按钮 + 打卡来源标签 */
  const importBtn = page.locator('button:has-text("导入考勤机数据")')
  ck('出现「导入考勤机数据」按钮', (await importBtn.count()) === 1, String(await importBtn.count()))

  const machineTag = page.locator('.el-table .el-tag:has-text("考勤机")')
  const machineCount = await machineTag.count()
  ck('明细表标出「考勤机」来源', machineCount >= 2, `标签数=${machineCount}`)

  const colHeads = await page.locator('.el-tabs__content .el-table__header th').allInnerTexts()
  ck('明细表含上班/下班打卡列', colHeads.some((h) => h.includes('上班打卡')) && colHeads.some((h) => h.includes('下班打卡')),
    colHeads.slice(0, 12).join('|'))

  /* 漏卡页签 */
  ck('首屏未取漏卡清单（懒加载）', hit('/hr/attendance/missing').length === 0,
    String(hit('/hr/attendance/missing').length))

  await page.locator('.el-tabs__item:has-text("漏卡待处理")').click()
  await page.waitForTimeout(1200)
  ck('切到漏卡页签后取数', okHit('/hr/attendance/missing'), String(hit('/hr/attendance/missing').length))

  const missingText = await page.locator('.el-tab-pane:visible .el-table').innerText().catch(() => '')
  ck('漏卡清单含「缺下班卡」', missingText.includes('缺下班卡'), missingText.slice(0, 120))
  ck('漏卡清单含员工姓名', missingText.includes('张明'), '')
  ck('漏卡清单显示补卡进度列', missingText.includes('未处理'), '')

  const alertText = await page.locator('.el-alert:visible').innerText().catch(() => '')
  ck('漏卡页有制度说明（不占 3 次额度）', alertText.includes('3 次'), alertText.slice(0, 80))

  const p = path.join(__dirname, '..', '..', '.runtime', 'attendance_import.png')
  await page.screenshot({ path: p, fullPage: false })

  ck('管理员视角无运行时错误', realErrors(errors).length === 0, realErrors(errors).slice(0, 2).join(' | '))

  /* ---------- 2. 员工自助视角 ---------- */

  const ctx2 = await browser.newContext({ viewport: { width: 1440, height: 950 } })
  const p2 = await ctx2.newPage()
  const errors2 = []
  p2.on('pageerror', (e) => errors2.push('pageerror: ' + e.message))
  p2.on('console', (m) => {
    if (m.type() === 'error') errors2.push('console: ' + m.text())
  })
  await login(p2, BASE, 'tech_admin', 'qwe@123456')
  await p2.goto(BASE + '/self/my-attendance', { waitUntil: 'networkidle' })
  await p2.waitForTimeout(1500)

  ck('自助页可访问', p2.url().includes('/self/my-attendance'), p2.url().replace(BASE, ''))
  const bodyText = await p2.locator('body').innerText()
  ck('自助页有「漏卡待处理」区块', bodyText.includes('漏卡待处理'), '')
  ck('自助页列出本人漏卡', bodyText.includes('缺下班卡'), '')
  ck('自助页提示不占额度', bodyText.includes('不占') || bodyText.includes('不计入'), '')

  const suppBtn = p2.locator('button:has-text("补下班卡")')
  ck('出现「补下班卡」按钮', (await suppBtn.count()) >= 1, String(await suppBtn.count()))
  if (await suppBtn.count()) {
    await suppBtn.first().click()
    await p2.waitForTimeout(700)
    const dlg = p2.locator('.el-dialog:visible')
    const dlgText = await dlg.innerText().catch(() => '')
    ck('补卡弹窗打开且字段齐全',
      ['补卡日期', '补卡方向', '补卡时间', '补卡原因'].every((t) => dlgText.includes(t)),
      dlgText.slice(0, 100))
    ck('弹窗说明不占额度', dlgText.includes('不计入') || dlgText.includes('不占'), '')
    await p2.locator('.el-dialog:visible button:has-text("取消")').click().catch(() => {})
  }
  ck('自助视角无运行时错误', realErrors(errors2).length === 0, realErrors(errors2).slice(0, 2).join(' | '))

  await browser.close()

  /* ---------- 3. 清理 ---------- */
  ck('夹具清理', seed('--clean').includes('cleaned'), '')

  process.exitCode = summary()
})()
