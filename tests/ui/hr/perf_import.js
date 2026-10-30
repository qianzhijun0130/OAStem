// BIZ-OPT-10（结果导入）UI 回归：导入入口、模板下载、来源标签。
//
// 数据由 seed_perf_import_fixture.py 造（周期 17 的张明，来源=导入），跑完清掉。
// 不依赖库里既有数据——否则别的用例清过场就会判红（夹具债上一轮刚还过）。
const path = require('path')
const { execFileSync } = require('child_process')
const { launch, login, realErrors, createReport } = require('../lib/browser')

const BASE = 'http://127.0.0.1:3000'
const PY = 'C:/Users/qianz/.workbuddy/binaries/python/envs/default/Scripts/python.exe'
const SEED = path.join(__dirname, '..', '..', 'api', 'hr', 'seed_perf_import_fixture.py')
const { ck, summary } = createReport()

function seed(flag) {
  const args = [SEED]
  if (flag) args.push(flag)
  return execFileSync(PY, args, { encoding: 'utf8', timeout: 120000 }).trim()
}

;(async () => {
  const seedOut = seed()
  ck('夹具准备（导入 1 人）', seedOut.includes('imported=1'), seedOut)

  const { browser, page, errors } = await launch({ width: 1500, height: 950 })
  const calls = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\/hr\/perf/.test(url)) return
    let code = null
    try {
      code = (await res.json())?.code
    } catch {
      code = 'no-json'
    }
    calls.push({ url: url.replace(BASE, ''), status: res.status(), code })
  })
  const okHit = (frag) => calls.some((c) => c.url.includes(frag) && c.status === 200
    && (c.code === 200 || c.code == null))

  await login(page, BASE)
  await page.goto(BASE + '/hr/performance', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  ck('绩效页可访问', page.url().includes('/hr/performance'), page.url().replace(BASE, ''))
  ck('默认停在考核表页签', okHit('/hr/perf/sheet/page'), JSON.stringify(
    calls.filter((c) => c.url.includes('sheet/page')).length))

  /* 入口按钮 */
  const tplBtn = page.locator('button:has-text("下载模板")')
  const impBtn = page.locator('button:has-text("导入结果")')
  ck('出现「下载模板」按钮', (await tplBtn.count()) === 1, String(await tplBtn.count()))
  ck('出现「导入结果」按钮', (await impBtn.count()) === 1, String(await impBtn.count()))

  /* 来源列与「导入」标签 */
  const heads = await page.locator('.el-table__header th').allInnerTexts()
  ck('表格有「来源」列', heads.some((h) => h.trim() === '来源'), heads.slice(0, 10).join('|'))
  const importTags = page.locator('.el-table .el-tag:has-text("导入")')
  ck('导入的成绩标出「导入」来源', (await importTags.count()) >= 1,
    `标签数=${await importTags.count()}`)
  const bodyText = await page.locator('.el-table__body').innerText().catch(() => '')
  ck('列表含该员工数据', bodyText.includes('张明'), bodyText.slice(0, 80))

  /* 模板下载 */
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 15000 }).catch(() => null),
    tplBtn.click(),
  ])
  // 下载走的是浏览器原生下载通道，不产生页面级 response 事件——只能断言 download 事件与文件名
  const fileName = download ? await download.suggestedFilename() : ''
  ck('点「下载模板」触发下载', !!download, fileName || '未触发')
  ck('模板文件名来自后端（含周期名）', fileName.includes('绩效结果导入模板'), fileName)

  await page.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'perf_import.png') })
  ck('无运行时错误', realErrors(errors).length === 0, realErrors(errors).slice(0, 2).join(' | '))

  await browser.close()
  ck('夹具清理', seed('--clean').includes('cleaned'), '')

  process.exitCode = summary()
})()
