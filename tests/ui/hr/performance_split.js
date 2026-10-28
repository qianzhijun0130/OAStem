// FE-OPT-06「绩效管理」拆分回归：1367 行 → 1 容器 + 5 tab + 4 弹窗（含 1 个明细抽屉）。
// 本页跨 tab 依赖最多：周期被 3 个页签共用、方案/指标下拉互为对方的弹窗选项。
// 因此除了「各 tab 能取数、弹窗能开」，还要盯三件事：
//   ① 周期先于考核表加载（列表要拿当前周期做回落筛选）
//   ② 两串下拉改为「谁用谁拉」，首屏不该出现
//   ③ 权重合计的实时校验与按钮禁用（方案弹窗最核心的交互）
const path = require('path')
const { launch, login, realErrors, createReport } = require('../lib/browser')

const BASE = 'http://127.0.0.1:3000'
const { ck, summary } = createReport()

;(async () => {
  const { browser, page, errors } = await launch({ width: 1500, height: 950 })

  const calls = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\/hr\/perf/.test(url)) {
      return
    }
    let code = null
    try {
      const body = await res.json()
      code = body && body.code
    } catch {
      code = 'no-json'
    }
    calls.push({ url: url.replace(BASE, ''), method: res.request().method(), status: res.status(), code })
  })
  const hit = (frag) => calls.filter((c) => c.url.includes(frag))
  const okHit = (frag) => hit(frag).some((c) => c.status === 200 && (c.code === 200 || c.code == null))
  const pane = () => page.locator('.el-tab-pane:visible')
  const rows = () => pane().locator('.el-table__body tr.el-table__row').count()
  const heads = async () =>
    (await pane().locator('.el-table__header th').allInnerTexts()).map((s) => s.trim())

  await login(page, BASE)
  await page.goto(BASE + '/hr/performance', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  ck('页面可访问', page.url().includes('/hr/performance'), page.url().replace(BASE, ''))

  /* ---------- 1. 页签与首屏请求 ---------- */
  const tabs = (await page.locator('.el-tabs__item').allInnerTexts()).map((s) => s.trim())
  ck(
    '五个页签齐全',
    ['考核表', '考核周期', '等级分布', '绩效方案', '指标库'].every((t) => tabs.includes(t)),
    tabs.join('|'),
  )

  ck('首屏取周期', okHit('/hr/perf/cycle/page'), JSON.stringify(hit('/hr/perf/cycle/page').map((c) => c.url)))
  // 页面 onMounted 与子页签都会 ensureLoaded，必须共用同一个在途 Promise，只发一条
  ck('周期只请求一次（并发去重）', hit('/hr/perf/cycle/page').length === 1, String(hit('/hr/perf/cycle/page').length))
  ck(
    '考核表带当前周期筛选（周期先于列表就位）',
    hit('/hr/perf/sheet/page').some((c) => /cycleId=\d+/.test(c.url)),
    JSON.stringify(hit('/hr/perf/sheet/page').map((c) => c.url)),
  )
  ck('首屏未取方案下拉', hit('/hr/perf/plan/options').length === 0, String(hit('/hr/perf/plan/options').length))
  ck('首屏未取指标下拉', hit('/hr/perf/indicator/options').length === 0, String(hit('/hr/perf/indicator/options').length))
  ck('首屏未取等级分布', hit('/hr/perf/distribution').length === 0, String(hit('/hr/perf/distribution').length))

  /* ---------- 2. 考核表页签 ---------- */
  const sheetRows = await rows()
  ck('考核表 2 行（当前周期）', sheetRows === 2, String(sheetRows))
  const sheetHeads = await heads()
  ck(
    '考核表列结构正确',
    ['周期', '工号', '姓名', '部门', '直属上级', '目标', '自评', '上级评分', '总分', '等级', '系数', '申诉', '操作'].every(
      (h) => sheetHeads.some((x) => x.includes(h)),
    ),
    sheetHeads.join('|'),
  )
  ck('有导出按钮', await pane().locator('[data-action="export"]').isVisible(), '')

  // 明细抽屉
  await pane().locator('[data-action="detail"]').first().click()
  await page.waitForTimeout(900)
  const drawer = page.locator('.el-drawer:visible')
  ck('明细抽屉打开', await drawer.isVisible(), '')
  const drawerText = await drawer.innerText()
  ck(
    '抽屉含基本信息与总分/等级',
    ['姓名', '工号', '部门', '周期', '阶段', '直属上级', '自评总分', '上级评分', '最终总分', '等级', '系数', '确认'].every((t) =>
      drawerText.includes(t),
    ),
    '',
  )
  const itemHeads = (await drawer.locator('.perf__items .el-table__header th').allInnerTexts()).map((s) => s.trim())
  ck(
    '抽屉指标表列完整',
    ['指标', '类型', '权重', '目标值', '自评', '上级评分', '加权分'].every((h) => itemHeads.some((x) => x.includes(h))),
    itemHeads.join('|'),
  )
  await drawer.locator('.el-drawer__close-btn').click()
  await page.waitForTimeout(400)

  /* ---------- 3. 考核周期页签 ---------- */
  await page.locator('.el-tabs__item:has-text("考核周期")').click()
  await page.waitForTimeout(800)
  const cycleRows = await rows()
  ck('周期列表 1 行', cycleRows === 1, String(cycleRows))
  const cycleHeads = await heads()
  ck(
    '周期列结构正确',
    ['周期', '编码', '方案', '起止', '阶段', '进度', '操作'].every((h) => cycleHeads.some((x) => x.includes(h))),
    cycleHeads.join('|'),
  )
  ck(
    '周期行有推进/归档/补齐/删除四个动作',
    (await pane().locator('[data-action="advance"]').count()) === 1 &&
      (await pane().locator('[data-action="archive"]').count()) === 1 &&
      (await pane().locator('[data-action="generate"]').count()) === 1 &&
      (await pane().locator('[data-action="delete"]').count()) === 1,
    '',
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(900)
  const cycleDlg = page.locator('.el-dialog:visible:has-text("绩效考核周期")')
  ck('新建周期弹窗打开', await cycleDlg.isVisible(), '')
  const cycleText = await cycleDlg.innerText()
  ck(
    '周期弹窗字段完整',
    ['周期编码', '周期名称', '绩效方案', '起止日期', '目标设定截止', '自评截止', '评分截止', '校准截止', '确认截止'].every(
      (t) => cycleText.includes(t),
    ),
    '',
  )
  ck('打开弹窗才拉方案下拉', okHit('/hr/perf/plan/options'), JSON.stringify(hit('/hr/perf/plan/options').map((c) => c.url)))
  await cycleDlg.locator('.el-form-item:has-text("绩效方案") .el-select').click()
  await page.waitForTimeout(400)
  const planOptionCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  ck('方案下拉有选项', planOptionCount > 0, `options=${planOptionCount}`)
  await page.keyboard.press('Escape')
  await cycleDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 4. 等级分布页签 ---------- */
  await page.locator('.el-tabs__item:has-text("等级分布")').click()
  await page.waitForTimeout(900)
  ck('分布页签取数', okHit('/hr/perf/distribution'), JSON.stringify(hit('/hr/perf/distribution').map((c) => c.url)))
  const distRows = await rows()
  ck('分布表有数据', distRows >= 1, String(distRows))
  const distHeads = await heads()
  ck(
    '分布列结构正确',
    ['部门', '人数', 'A', 'B', 'C', 'D', 'A 占比', '平均分'].every((h) => distHeads.some((x) => x.includes(h))),
    distHeads.join('|'),
  )
  const distText = await pane().innerText()
  ck('分布页有「仅预警不拦截」说明', distText.includes('不拦截'), '')

  /* ---------- 5. 绩效方案页签 ---------- */
  await page.locator('.el-tabs__item:has-text("绩效方案")').click()
  await page.waitForTimeout(900)
  const planRows = await rows()
  ck('方案列表 1 行', planRows === 1, String(planRows))
  const planHeads = await heads()
  ck(
    '方案列结构正确',
    ['编码', '名称', '周期类型', '适用部门', '适用序列', '权重合计', '状态', '操作'].every((h) =>
      planHeads.some((x) => x.includes(h)),
    ),
    planHeads.join('|'),
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(900)
  const planDlg = page.locator('.el-dialog:visible:has-text("绩效方案")')
  ck('新建方案弹窗打开', await planDlg.isVisible(), '')
  ck('打开弹窗才拉指标下拉', okHit('/hr/perf/indicator/options'), JSON.stringify(hit('/hr/perf/indicator/options').map((c) => c.url)))

  const planText = await planDlg.innerText()
  ck(
    '方案弹窗含编码/名称/周期类型/等级映射',
    ['方案编码', '方案名称', '周期类型', '适用序列', '状态', '适用部门', '强制分布', '等级映射与系数'].every((t) =>
      planText.includes(t),
    ),
    '',
  )
  ck('默认权重合计 100%', /权重合计：100%/.test(planText), planText.match(/权重合计：[\d.]+%/)?.[0] || '')
  const removeBtn = planDlg.locator('[data-action="remove-indicator"]').first()
  await removeBtn.click()
  await page.waitForTimeout(400)
  const afterText = await planDlg.innerText()
  ck('移除唯一指标后合计变 0%', /权重合计：0%/.test(afterText), afterText.match(/权重合计：[\d.]+%/)?.[0] || '')
  ck(
    '权重不等于 100% 时保存按钮禁用',
    await planDlg.locator('[data-action="submit"]').isDisabled(),
    '',
  )
  await planDlg.locator('[data-action="add-indicator"]').click()
  await page.waitForTimeout(300)
  ck('加回指标行后仍为 0%（权重需手填）', /权重合计：0%/.test(await planDlg.innerText()), '')
  await planDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 6. 指标库页签 ---------- */
  await page.locator('.el-tabs__item:has-text("指标库")').click()
  await page.waitForTimeout(900)
  const indicatorRows = await rows()
  ck('指标列表 3 行', indicatorRows === 3, String(indicatorRows))
  const indicatorHeads = await heads()
  ck(
    '指标列结构正确',
    ['编码', '名称', '类型', '单位', '定义', '操作'].every((h) => indicatorHeads.some((x) => x.includes(h))),
    indicatorHeads.join('|'),
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(500)
  const indicatorDlg = page.locator('.el-dialog:visible:has-text("绩效指标")')
  ck('新建指标弹窗打开', await indicatorDlg.isVisible(), '')
  const indicatorText = await indicatorDlg.innerText()
  ck(
    '指标弹窗字段完整',
    ['指标编码', '指标名称', '指标类型', '计量单位', '指标定义', '评分标准'].every((t) => indicatorText.includes(t)),
    '',
  )
  await indicatorDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  await page.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'perf_split.png'), fullPage: true })

  const real = realErrors(errors)
  ck('无运行时错误', real.length === 0, real.slice(0, 3).join(' || '))

  await browser.close()
  const failed = summary()
  process.exit(failed ? 1 : 0)
})()
