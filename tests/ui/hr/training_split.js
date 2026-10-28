// FE-OPT-06「培训管理」拆分回归：961 行页面 → 1 容器 + 5 tab + 4 弹窗。
// 重点盯三件事：① 页签懒加载没有退化成首屏全加载；② 报名/成绩两个页签共用同一份
// 期次花名册（拆开后最容易各自持状态、回切丢选中）；③ 抽出去的弹窗校验仍然生效。
const path = require('path')
const { launch, login, realErrors, createReport } = require('../lib/browser')

const BASE = 'http://127.0.0.1:3000'
const { ck, summary } = createReport()

;(async () => {
  const { browser, page, errors } = await launch({ width: 1500, height: 950 })

  const calls = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\/hr\/train|\/api\/system\/user/.test(url)) {
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
  await page.goto(BASE + '/hr/training', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)

  ck('页面可访问', page.url().includes('/hr/training'), page.url().replace(BASE, ''))

  /* ---------- 1. 页签与懒加载 ---------- */
  const tabs = (await page.locator('.el-tabs__item').allInnerTexts()).map((s) => s.trim())
  ck(
    '五个页签齐全',
    ['培训课程', '培训期次', '报名管理', '成绩管理', '培训统计'].every((t) => tabs.includes(t)),
    tabs.join('|'),
  )

  const coursePageCalls = hit('/hr/train/course/page')
  ck('首屏拉课程下拉（pageSize=200）', coursePageCalls.some((c) => c.url.includes('pageSize=200')), JSON.stringify(coursePageCalls.map((c) => c.url)))
  ck('首屏未拉期次列表（pageSize=10）', !hit('/hr/train/session/page').some((c) => c.url.includes('pageSize=10')), JSON.stringify(hit('/hr/train/session/page').map((c) => c.url)))
  ck('首屏未拉统计', hit('/hr/train/statistics').length === 0, String(hit('/hr/train/statistics').length))

  /* ---------- 2. 课程页签 ---------- */
  // 库里 6 条课程、其中 5 条已逻辑删除 → 列表只应看到 1 条（别用 COUNT(*)，会算进软删行）
  const courseRows = await rows()
  ck('课程列表 1 行（软删已排除）', courseRows === 1, String(courseRows))
  const courseHeads = await heads()
  ck(
    '课程列结构正确',
    ['编码', '课程名称', '类型', '讲师', '学时', '必修', '状态', '操作'].every((h) => courseHeads.some((x) => x.includes(h))),
    courseHeads.join('|'),
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(400)
  const courseDlg = page.locator('.el-dialog:visible:has-text("新增课程")')
  ck('新增课程弹窗打开', await courseDlg.isVisible(), '')
  const courseDlgText = await courseDlg.innerText()
  ck(
    '课程弹窗字段完整',
    ['课程编码', '课程名称', '课程类型', '讲师类型', '学时', '是否必修', '及格线', '培训成本'].every((t) =>
      courseDlgText.includes(t),
    ),
    '',
  )
  // 内部讲师才显示人员下拉，切到外部讲师应换成姓名/机构
  const teacherSelect = courseDlg.locator('.el-form-item:has-text("讲师类型") .el-select')
  await teacherSelect.click()
  await page.waitForTimeout(300)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("外部讲师")').click()
  await page.waitForTimeout(300)
  ck('外部讲师切换到姓名/机构字段', (await courseDlg.innerText()).includes('讲师机构'), '')
  await courseDlg.locator('.el-form-item:has-text("讲师类型") .el-select').click()
  await page.waitForTimeout(300)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("内部讲师")').click()
  await page.waitForTimeout(300)

  await courseDlg.locator('.el-dialog__footer .el-button--primary').click()
  await page.waitForTimeout(600)
  const courseErrs = await courseDlg.locator('.el-form-item__error').count()
  ck('课程空表单被必填校验拦住', courseErrs >= 2, `error=${courseErrs}`)
  const coursePosts = calls.filter((c) => c.method === 'POST' && /\/hr\/train\/course$/.test(c.url)).length
  ck('校验未通过时不提交课程', coursePosts === 0, String(coursePosts))
  await courseDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  /* ---------- 3. 期次页签 ---------- */
  await page.locator('.el-tabs__item:has-text("培训期次")').click()
  await page.waitForTimeout(1000)
  ck('期次页签自己取数', hit('/hr/train/session/page').some((c) => c.url.includes('pageSize=10')), '')
  // 18 条期次里 15 条已逻辑删除 → 有效 3 条
  const sessionRows = await rows()
  ck('期次列表 3 行（软删已排除）', sessionRows === 3, String(sessionRows))
  const sessionHeads = await heads()
  ck(
    '期次列结构正确',
    ['课程', '期次', '开始', '结束', '地点', '名额', '状态', '操作'].every((h) => sessionHeads.some((x) => x.includes(h))),
    sessionHeads.join('|'),
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(400)
  const sessionDlg = page.locator('.el-dialog:visible:has-text("新增期次")')
  ck('新增期次弹窗打开', await sessionDlg.isVisible(), '')
  const sessionDlgText = await sessionDlg.innerText()
  ck(
    '期次弹窗字段完整',
    ['课程', '期次名称', '开始时间', '结束时间', '地点', '名额', '报名截止', '签到方式'].every((t) =>
      sessionDlgText.includes(t),
    ),
    '',
  )
  const hintFont = await sessionDlg.locator('.training__hint').first().evaluate((el) => getComputedStyle(el).fontSize)
  ck('共享样式生效（hint 12px）', hintFont === '12px', hintFont)
  await sessionDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  /* ---------- 4. 报名页签 ---------- */
  await page.locator('.el-tabs__item:has-text("报名管理")').click()
  await page.waitForTimeout(800)
  ck('未选期次时列表为空', (await rows()) === 0, String(await rows()))

  await pane().locator('[data-action="batch"]').click()
  await page.waitForTimeout(500)
  const warn = await page.locator('.el-message').last().innerText().catch(() => '')
  ck('未选期次点批量报名被拦', warn.includes('请先选择期次'), warn)
  await page.waitForTimeout(3200) // ElMessage 默认 3 秒消失，避免干扰后续断言

  // 选第一个期次 → 看板 watch 触发取花名册
  await pane().locator('.el-select').first().click()
  await page.waitForTimeout(400)
  const sessionOptions = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  ck('期次下拉有选项（共享 options）', sessionOptions > 0, `options=${sessionOptions}`)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await page.waitForTimeout(1000)
  ck('选期次后拉花名册', okHit('/enrolls'), JSON.stringify(hit('/enrolls').map((c) => c.url)))
  const enrollRows = await rows()
  ck('花名册有数据', enrollRows > 0, `rows=${enrollRows}`)
  const enrollHeads = await heads()
  ck(
    '报名列结构正确',
    ['姓名', '部门', '报名方式', '状态', '签到时间', '成绩', '指派原因', '操作'].every((h) =>
      enrollHeads.some((x) => x.includes(h)),
    ),
    enrollHeads.join('|'),
  )
  ck('报名行有成绩/签到两个操作', (await pane().locator('[data-action="score"]').count()) > 0 && (await pane().locator('[data-action="checkin"]').count()) > 0, '')

  await pane().locator('[data-action="batch"]').click()
  await page.waitForTimeout(400)
  const batchDlg = page.locator('.el-dialog:visible:has-text("批量指派报名")')
  ck('批量报名弹窗打开', await batchDlg.isVisible(), '')
  const batchText = await batchDlg.innerText()
  ck(
    '批量报名含员工/指派原因',
    ['员工', '指派原因'].every((t) => batchText.includes(t)) && batchText.includes('超额指派须说明原因'),
    '',
  )
  await batchDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  // 成绩录入弹窗（从报名页打开）
  await pane().locator('[data-action="score"]').first().click()
  await page.waitForTimeout(400)
  const scoreDlg = page.locator('.el-dialog:visible:has-text("成绩录入")')
  ck('成绩弹窗打开', await scoreDlg.isVisible(), '')
  const scoreText = await scoreDlg.innerText()
  ck('成绩弹窗含成绩/考核方式/满意度/评语', ['成绩', '考核方式', '满意度', '评语'].every((t) => scoreText.includes(t)), '')
  await scoreDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(300)

  /* ---------- 5. 成绩页签：共享同一份看板 ---------- */
  const enrollCallsBefore = hit('/enrolls').length
  await page.locator('.el-tabs__item:has-text("成绩管理")').click()
  await page.waitForTimeout(900)
  const scoreRows = await rows()
  ck('成绩页签沿用报名页选中的期次（行数一致）', scoreRows === enrollRows, `报名=${enrollRows} 成绩=${scoreRows}`)
  ck('切页签不重复拉花名册', hit('/enrolls').length === enrollCallsBefore, `${enrollCallsBefore}→${hit('/enrolls').length}`)
  const scoreHeads = await heads()
  ck(
    '成绩列结构正确',
    ['姓名', '部门', '状态', '成绩', '及格', '满意度', '操作'].every((h) => scoreHeads.some((x) => x.includes(h))),
    scoreHeads.join('|'),
  )

  /* ---------- 6. 统计页签 ---------- */
  await page.locator('.el-tabs__item:has-text("培训统计")').click()
  await page.waitForTimeout(1000)
  ck('统计页签取数', okHit('/hr/train/statistics'), JSON.stringify(hit('/hr/train/statistics').map((c) => c.url)))
  const statText = await pane().innerText()
  ck(
    '八个指标齐全',
    ['人均学时', '总学时', '必修完成率', '课程满意度', '在职人数', '培训总投入', '应完成必修', '已完成必修'].every((t) =>
      statText.includes(t),
    ),
    '',
  )
  const statTables = await pane().locator('.el-table').count()
  ck('按课程/按部门两张明细表', statTables === 2, String(statTables))

  await page.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'training_split.png'), fullPage: true })

  const real = realErrors(errors)
  ck('无运行时错误', real.length === 0, real.slice(0, 3).join(' || '))

  await browser.close()
  const failed = summary()
  process.exit(failed ? 1 : 0)
})()
