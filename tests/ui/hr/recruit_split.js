// FE-OPT-06「招聘管理」拆分回归：1834 行 → 1 容器 + 6 tab + 9 弹窗 + 1 抽屉。
// 这条流水线（需求→职位→候选人→面试→Offer→看板）上，每一环都会改动下一环的下拉数据，
// 所以除了「各 tab 能取数、弹窗能开」，重点验：
//   ① 六份共享下拉改成「谁用谁取」后，首屏只有部门树 + 需求列表，没有被 6 连发拖住
//   ② 每个弹窗打开时才补它需要的下拉（users / positions / demands / posts / candidates）
//   ③ 业务前置规则还在：Offer 候选人只放终试（stage=4），阶段选「淘汰」才出淘汰原因
const path = require('path')
const { launch, login, realErrors, createReport } = require('../lib/browser')

const BASE = 'http://127.0.0.1:3000'
const { ck, summary } = createReport()

;(async () => {
  const { browser, page, errors } = await launch({ width: 1500, height: 950 })

  const calls = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\/(hr\/recruit|system\/dept|hr\/position|system\/user)/.test(url)) {
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
  const openTab = async (name) => {
    await page.locator(`.el-tabs__item:has-text("${name}")`).click()
    await page.waitForTimeout(900)
  }

  await login(page, BASE)
  await page.goto(BASE + '/hr/recruit', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1300)

  ck('页面可访问', page.url().includes('/hr/recruit'), page.url().replace(BASE, ''))

  /* ---------- 1. 页签与首屏请求 ---------- */
  const tabs = (await page.locator('.el-tabs__item').allInnerTexts()).map((s) => s.trim())
  ck(
    '六个页签齐全',
    ['招聘需求', '招聘职位', '候选人', '面试', 'Offer', '招聘看板'].every((t) => tabs.includes(t)),
    tabs.join('|'),
  )

  ck('首屏取部门树', hit('/system/dept').length > 0, JSON.stringify(hit('/system/dept').map((c) => c.url)))
  ck(
    '首屏取需求列表（pageSize=10）',
    hit('/hr/recruit/demand/page').some((c) => c.url.includes('pageSize=10')),
    JSON.stringify(hit('/hr/recruit/demand/page').map((c) => c.url)),
  )
  ck('首屏未取需求全量下拉（pageSize=200）', hit('/hr/recruit/demand/page').every((c) => !c.url.includes('pageSize=200')), '')
  ck('首屏未取职位列表', hit('/hr/recruit/post/page').length === 0, String(hit('/hr/recruit/post/page').length))
  ck('首屏未取候选人员工下拉', hit('/system/user').length === 0, String(hit('/system/user').length))

  /* ---------- 2. 招聘需求 ---------- */
  const demandRows = await rows()
  ck('需求列表 2 行', demandRows === 2, String(demandRows))
  const demandHeads = await heads()
  ck(
    '需求列结构正确',
    ['需求编码', '部门', '岗位', '人数', '紧急度', '期望到岗', '负责HR', '已招', '缺口', '状态', '操作'].every((h) =>
      demandHeads.some((x) => x.includes(h)),
    ),
    demandHeads.join('|'),
  )
  ck(
    '需求行有审批/编辑/关闭三个动作',
    (await pane().locator('[data-action="approve"]').count()) === 2 &&
      (await pane().locator('[data-action="edit"]').count()) === 2 &&
      (await pane().locator('[data-action="close"]').count()) === 2,
    '',
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(1100)
  const demandDlg = page.locator('.el-dialog:visible:has-text("新增需求")')
  ck('新增需求弹窗打开', await demandDlg.isVisible(), '')
  const demandText = await demandDlg.innerText()
  ck(
    '需求弹窗字段完整',
    ['需求部门', '需求岗位', '招聘人数', '紧急度', '期望到岗', '学历要求', '经验要求', '技能要求', '薪资范围', '招聘原因', '负责HR'].every(
      (t) => demandText.includes(t),
    ),
    '',
  )
  ck('打开需求弹窗才拉员工与岗位下拉', okHit('/system/user') && hit('/hr/position').length > 0, JSON.stringify(hit('/hr/position').map((c) => c.url)))
  await demandDlg.locator('[data-action="submit"]').click()
  await page.waitForTimeout(600)
  const demandErrs = await demandDlg.locator('.el-form-item__error').count()
  ck('需求空表单被必填校验拦住', demandErrs >= 2, `error=${demandErrs}`)
  const demandPosts = calls.filter((c) => c.method === 'POST' && /\/hr\/recruit\/demand$/.test(c.url)).length
  ck('校验未通过时不提交需求', demandPosts === 0, String(demandPosts))
  await demandDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 3. 招聘职位 ---------- */
  await openTab('招聘职位')
  const postRows = await rows()
  ck('职位列表 1 行', postRows === 1, String(postRows))
  const postHeads = await heads()
  ck(
    '职位列结构正确',
    ['关联需求', '职位名称', '工作地点', '薪资范围', '人数', '渠道', '负责HR', '投递', '入职', '状态', '操作'].every((h) =>
      postHeads.some((x) => x.includes(h)),
    ),
    postHeads.join('|'),
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(1100)
  const postDlg = page.locator('.el-dialog:visible:has-text("新增职位")')
  ck('新增职位弹窗打开', await postDlg.isVisible(), '')
  const postText = await postDlg.innerText()
  ck(
    '职位弹窗字段完整',
    ['关联需求', '职位名称', '工作地点', '薪资范围', '招聘人数', '渠道', '状态', '负责HR'].every((t) =>
      postText.includes(t),
    ),
    '',
  )
  ck(
    '打开职位弹窗才拉需求全量下拉',
    hit('/hr/recruit/demand/page').some((c) => c.url.includes('pageSize=200')),
    JSON.stringify(hit('/hr/recruit/demand/page').map((c) => c.url)),
  )
  await postDlg.locator('.el-form-item:has-text("关联需求") .el-select').click()
  await page.waitForTimeout(400)
  const demandOptCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  ck('关联需求下拉有选项', demandOptCount === 2, `options=${demandOptCount}`)
  await page.keyboard.press('Escape')
  await postDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 4. 候选人 ---------- */
  await openTab('候选人')
  const candRows = await rows()
  ck('候选人列表 2 行', candRows === 2, String(candRows))
  const candHeads = await heads()
  ck(
    '候选人列结构正确',
    ['姓名', '手机号', '性别', '应聘职位', '来源', '阶段', '面试次数', '内推人', '操作'].every((h) =>
      candHeads.some((x) => x.includes(h)),
    ),
    candHeads.join('|'),
  )

  // 详情抽屉
  await pane().locator('[data-action="detail"]').first().click()
  await page.waitForTimeout(900)
  const drawer = page.locator('.el-drawer:visible')
  ck('候选人详情抽屉打开', await drawer.isVisible(), '')
  const drawerText = await drawer.innerText()
  ck(
    '抽屉含脱敏手机号/当前阶段/面试记录',
    ['姓名', '手机号(脱敏)', '当前阶段', '面试次数', '面试记录'].every((t) => drawerText.includes(t)),
    '',
  )
  await drawer.locator('.el-drawer__close-btn').click()
  await page.waitForTimeout(400)

  // 阶段流转：选「淘汰」才出现淘汰原因
  await pane().locator('[data-action="stage"]').first().click()
  await page.waitForTimeout(500)
  const stageDlg = page.locator('.el-dialog:visible:has-text("阶段流转")')
  ck('阶段流转弹窗打开', await stageDlg.isVisible(), '')
  ck('默认不显示淘汰原因', !(await stageDlg.innerText()).includes('淘汰原因'), '')
  await stageDlg.locator('.el-form-item:has-text("目标阶段") .el-select').click()
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("淘汰")').first().click()
  await page.waitForTimeout(400)
  ck('选「淘汰」后出现淘汰原因/类型', (await stageDlg.innerText()).includes('淘汰原因'), '')
  const hintFont = await stageDlg.locator('.recruit__hint').first().evaluate((el) => getComputedStyle(el).fontSize)
  ck('共享样式生效（hint 12px）', hintFont === '12px', hintFont)
  await stageDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  // 新增候选人：含查重入口
  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(1200)
  const candDlg = page.locator('.el-dialog:visible:has-text("新增候选人")')
  ck('新增候选人弹窗打开', await candDlg.isVisible(), '')
  const candText = await candDlg.innerText()
  ck(
    '候选人弹窗字段完整（含查重/授权）',
    ['姓名', '手机号', '查重', '邮箱', '性别', '应聘职位', '关联需求', '来源', '内推人', '已授权', '进人才库'].every((t) =>
      candText.includes(t),
    ),
    '',
  )
  ck(
    '打开候选人弹窗才拉职位全量下拉',
    hit('/hr/recruit/post/page').some((c) => c.url.includes('pageSize=200')),
    JSON.stringify(hit('/hr/recruit/post/page').map((c) => c.url)),
  )
  // 手机号格式校验（前端 rules 里带 pattern）
  await candDlg.locator('.el-form-item:has-text("手机号") input').fill('123')
  await candDlg.locator('[data-action="submit"]').click()
  await page.waitForTimeout(600)
  const candErrText = await candDlg.locator('.el-form-item__error').allInnerTexts()
  ck('手机号格式校验生效', candErrText.some((t) => t.includes('手机号')), candErrText.join('|'))
  await candDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 5. 面试 ---------- */
  await openTab('面试')
  const interviewRows = await rows()
  ck('面试列表 1 行', interviewRows === 1, String(interviewRows))
  const interviewHeads = await heads()
  ck(
    '面试列结构正确',
    ['候选人', '职位', '轮次', '面试时间', '方式', '面试官', '评价', '结论', '状态', '操作'].every((h) =>
      interviewHeads.some((x) => x.includes(h)),
    ),
    interviewHeads.join('|'),
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(1100)
  const interviewDlg = page.locator('.el-dialog:visible:has-text("安排面试")')
  ck('安排面试弹窗打开', await interviewDlg.isVisible(), '')
  const interviewText = await interviewDlg.innerText()
  ck(
    '面试弹窗字段完整',
    ['候选人', '职位', '轮次', '面试时间', '方式', '地点/链接', '面试官', '备注'].every((t) => interviewText.includes(t)),
    '',
  )
  await interviewDlg.locator('.el-form-item:has-text("候选人") .el-select').click()
  await page.waitForTimeout(400)
  const candOptCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  ck('候选人下拉有 2 个选项', candOptCount === 2, `options=${candOptCount}`)
  await page.keyboard.press('Escape')
  await interviewDlg.locator('[data-action="submit"]').click()
  await page.waitForTimeout(600)
  const interviewErrs = await interviewDlg.locator('.el-form-item__error').count()
  ck('面试空表单被必填校验拦住', interviewErrs >= 2, `error=${interviewErrs}`)
  await interviewDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 6. Offer ---------- */
  await openTab('Offer')
  const offerRows = await rows()
  ck('Offer 列表 1 行', offerRows === 1, String(offerRows))
  const offerHeads = await heads()
  ck(
    'Offer 列结构正确',
    ['Offer 编号', '候选人', '岗位', '薪资', '报到日期', '发送方式', '状态', '操作'].every((h) =>
      offerHeads.some((x) => x.includes(h)),
    ),
    offerHeads.join('|'),
  )
  ck(
    'Offer 行有发出/接受/拒绝/转正四个动作',
    (await pane().locator('[data-action="send"]').count()) === 1 &&
      (await pane().locator('[data-action="accept"]').count()) === 1 &&
      (await pane().locator('[data-action="refuse"]').count()) === 1 &&
      (await pane().locator('[data-action="convert"]').count()) === 1,
    '',
  )

  await pane().locator('[data-action="create"]').click()
  await page.waitForTimeout(1100)
  const offerDlg = page.locator('.el-dialog:visible:has-text("发起 Offer")')
  ck('发起 Offer 弹窗打开', await offerDlg.isVisible(), '')
  const offerText = await offerDlg.innerText()
  ck(
    'Offer 弹窗字段完整',
    ['候选人', '职位', '岗位名称', '薪资', '试用期', '报到日期', '有效期至', '发送方式'].every((t) => offerText.includes(t)),
    '',
  )
  // 库里两名候选人均非终试（stage=1/7），下拉必须为空 —— 证明「仅终试」过滤生效
  await offerDlg.locator('.el-form-item:has-text("候选人") .el-select').click()
  await page.waitForTimeout(400)
  const offerCandOpts = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  const offerEmptyHint = await page.locator('.el-select-dropdown:visible .el-select-dropdown__empty').count()
  ck('Offer 候选人只放终试（当前库 0 人）', offerCandOpts === 0 && offerEmptyHint === 1, `options=${offerCandOpts} empty=${offerEmptyHint}`)
  await page.keyboard.press('Escape')
  await offerDlg.locator('.el-dialog__footer .el-button').first().click()
  await page.waitForTimeout(400)

  /* ---------- 7. 招聘看板 ---------- */
  await openTab('招聘看板')
  ck('看板取数', okHit('/hr/recruit/board'), JSON.stringify(hit('/hr/recruit/board').map((c) => c.url)))
  const boardText = await pane().innerText()
  ck(
    '四个核心指标齐全',
    ['在招职位数', '需求总人数', '已招到岗', '平均周期(天)'].every((t) => boardText.includes(t)),
    '',
  )
  const boardTables = await pane().locator('.el-table').count()
  ck('四张维度表齐全', boardTables === 4, String(boardTables))

  await page.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'recruit_split.png'), fullPage: true })

  const real = realErrors(errors)
  ck('无运行时错误', real.length === 0, real.slice(0, 3).join(' || '))

  await browser.close()
  const failed = summary()
  process.exit(failed ? 1 : 0)
})()
