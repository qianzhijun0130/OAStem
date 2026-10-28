// FE-OPT-06「假勤管理」拆分回归：页面从 972 行拆成 1 容器 + 4 tab + 4 弹窗后，
// 验证「拆出去的东西还在」——每个 tab 自己取数、每个弹窗能开且校验有效、
// 共享样式（非 scoped）仍然生效、首屏没有退化成一口气打七个请求。
const path = require('path')
const {
  launch,
  login,
  realErrors,
  createReport,
} = require('../lib/browser')

const BASE = 'http://127.0.0.1:3000'
const { ck, summary } = createReport()

;(async () => {
  const { browser, page, errors } = await launch({ width: 1500, height: 950 })

  // 记录所有 HR 请假/申请相关接口，用来判「哪个 tab 真的发了请求」以及懒加载是否成立
  const calls = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\/hr\/(leave|request)/.test(url)) {
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
  /** 精确匹配提交动作：`POST /hr/leave` 不能被 `/hr/leave/page` 等 GET 干扰 */
  const submitted = () => calls.filter((c) => c.method === 'POST' && /\/hr\/leave$/.test(c.url)).length

  await login(page, BASE)
  await page.goto(BASE + '/hr/leave', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  ck('页面可访问', page.url().includes('/hr/leave'), page.url().replace(BASE, ''))

  /* ---------- 1. 六个页签齐全 ---------- */
  const tabs = (await page.locator('.el-tabs__item').allInnerTexts()).map((s) => s.trim())
  const expectTabs = ['我的额度', '请假单', '加班', '出差', '补卡', '假别政策']
  ck('六个页签齐全', expectTabs.every((t) => tabs.includes(t)), tabs.join('|'))

  /* ---------- 2. 懒加载：首屏只取额度 ---------- */
  ck('首屏只请求额度', okHit('/hr/leave/quota/my'), JSON.stringify(calls.map((c) => c.url)))
  ck('首屏未请求请假列表', hit('/hr/leave/page').length === 0, String(hit('/hr/leave/page').length))
  ck('首屏未请求加班/出差/补卡列表', hit('/hr/request/page').length === 0, String(hit('/hr/request/page').length))
  ck('首屏未请求假别政策', hit('/hr/leave/policy').length === 0, String(hit('/hr/leave/policy').length))

  /* ---------- 3. 我的额度 tab ---------- */
  const quotaRows = await page.locator('.el-tab-pane:visible .el-table__body tr.el-table__row').count()
  ck('额度列表有数据', quotaRows >= 2, String(quotaRows))
  const quotaText = await page.locator('.el-tab-pane:visible .el-table').first().innerText()
  ck('额度表含年假/可用列', quotaText.includes('年假') && quotaText.includes('可用'), '')
  ck('额度页签有「调整额度」按钮', await page.locator('[data-action="adjust"]').isVisible(), '')

  // 非 scoped 样式：hint 必须是 12px（拆到子组件后若样式被 scoped 隔离就会回落 14px）
  const hintFont = await page
    .locator('.el-tab-pane:visible .hr-leave__hint')
    .first()
    .evaluate((el) => getComputedStyle(el).fontSize)
  ck('共享样式生效（hint 12px）', hintFont === '12px', hintFont)

  // 调整额度弹窗
  await page.locator('[data-action="adjust"]').click()
  await page.waitForTimeout(400)
  const adjustDlg = page.locator('.el-dialog:visible:has-text("调整假期额度")')
  ck('调整额度弹窗打开', await adjustDlg.isVisible(), '')
  const adjustText = await adjustDlg.innerText()
  ck('调整额度弹窗字段完整', ['员工ID', '假别', '总额度', '调整说明'].every((t) => adjustText.includes(t)), '')
  await adjustDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  /* ---------- 4. 请假单 tab ---------- */
  await page.locator('.el-tabs__item:has-text("请假单")').click()
  await page.waitForTimeout(1000)
  ck('切到请假单会取数', okHit('/hr/leave/page'), JSON.stringify(hit('/hr/leave/page')))
  const leaveHeaders = (await page.locator('.el-tab-pane:visible .el-table__header th').allInnerTexts()).map((s) => s.trim())
  ck(
    '请假表头完整',
    ['单据编号', '申请人', '部门', '假别', '起止时间', '天数', '状态', '操作'].every((h) =>
      leaveHeaders.some((x) => x.includes(h)),
    ),
    leaveHeaders.join('|'),
  )
  ck('请假页签有导出/发起按钮', (await page.locator('[data-action="export"]').count()) === 1 && (await page.locator('[data-action="create"]').count()) === 1, '')

  // 发起请假：必填校验必须真的拦住空表单（模板 ref 若被搬进 composable 会静默失效）
  await page.locator('.el-tab-pane:visible [data-action="create"]').click()
  await page.waitForTimeout(400)
  const leaveDlg = page.locator('.el-dialog:visible:has-text("发起请假")')
  ck('发起请假弹窗打开', await leaveDlg.isVisible(), '')
  const leaveDlgText = await leaveDlg.innerText()
  ck(
    '请假弹窗字段完整',
    ['假别', '开始时间', '结束时间', '请假天数', '事由'].every((t) => leaveDlgText.includes(t)),
    '',
  )
  await leaveDlg.locator('.el-dialog__footer .el-button--primary').click()
  await page.waitForTimeout(600)
  const leaveErrs = await leaveDlg.locator('.el-form-item__error').count()
  ck('空表单提交被必填校验拦住', leaveErrs >= 2, `error=${leaveErrs}`)
  ck('校验未通过时不发提交请求', submitted() === 0, String(submitted()))
  await leaveDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  /* ---------- 5. 三个业务页签各自取数 + 列结构 ---------- */
  const bizCases = [
    { tab: '加班', bizType: 1, cols: ['加班日期', '时长(h)', '补偿'] },
    { tab: '出差', bizType: 2, cols: ['出差地点', '天数', '预算'] },
    { tab: '补卡', bizType: 3, cols: ['补卡日期', '方向', '补卡时间'] },
  ]
  for (const c of bizCases) {
    await page.locator(`.el-tabs__item:has-text("${c.tab}")`).click()
    await page.waitForTimeout(900)
    const called = hit('/hr/request/page').some((x) => x.url.includes(`bizType=${c.bizType}`))
    ck(`${c.tab}页签自己取数`, called, JSON.stringify(hit('/hr/request/page').map((x) => x.url)))
    const heads = (await page.locator('.el-tab-pane:visible .el-table__header th').allInnerTexts()).map((s) => s.trim())
    ck(
      `${c.tab}列结构正确`,
      ['单据编号', ...c.cols, '状态', '操作'].every((h) => heads.some((x) => x.includes(h))),
      heads.join('|'),
    )
    ck(`${c.tab}页签有发起按钮`, await page.locator('.el-tab-pane:visible [data-action="create"]').isVisible(), '')
  }

  /* ---------- 6. 假别政策 tab ---------- */
  await page.locator('.el-tabs__item:has-text("假别政策")').click()
  await page.waitForTimeout(1000)
  ck('政策页签会取数', okHit('/hr/leave/policy'), JSON.stringify(hit('/hr/leave/policy')))
  const policyHeads = (await page.locator('.el-tab-pane:visible .el-table__header th').allInnerTexts()).map((s) => s.trim())
  ck(
    '政策表头完整',
    ['城市', '假别', '法定天数', '生效年度', '依据', '更新时间'].every((h) => policyHeads.some((x) => x.includes(h))),
    policyHeads.join('|'),
  )

  await page.locator('.el-tab-pane:visible [data-action="create"]').click()
  await page.waitForTimeout(600)
  const policyDlg = page.locator('.el-dialog:visible:has-text("新增假别政策")')
  ck('新增政策弹窗打开', await policyDlg.isVisible(), '')
  const policyDlgText = await policyDlg.innerText()
  ck(
    '政策弹窗字段完整',
    ['城市', '假别', '法定天数', '生效年度', '依据'].every((t) => policyDlgText.includes(t)),
    '',
  )
  ck('政策弹窗懒加载假别选项', okHit('/hr/leave/policy/types'), JSON.stringify(hit('/hr/leave/policy/types')))
  await policyDlg.locator('.el-select').first().click()
  await page.waitForTimeout(400)
  const typeOptions = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  ck('假别下拉有选项', typeOptions > 0, `options=${typeOptions}`)
  await page.keyboard.press('Escape')
  await policyDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  /* ---------- 7. 回到额度：不重复请求已成型的 tab ---------- */
  const quotaBefore = hit('/hr/leave/quota/my').length
  await page.locator('.el-tabs__item:has-text("我的额度")').click()
  await page.waitForTimeout(600)
  ck('返回额度页签不重复取数（tab 保持挂载）', hit('/hr/leave/quota/my').length === quotaBefore, `${quotaBefore}→${hit('/hr/leave/quota/my').length}`)

  await page.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'leave_split.png'), fullPage: true })

  const real = realErrors(errors)
  ck('无运行时错误', real.length === 0, real.slice(0, 3).join(' || '))

  await browser.close()
  const failed = summary()
  process.exit(failed ? 1 : 0)
})()
