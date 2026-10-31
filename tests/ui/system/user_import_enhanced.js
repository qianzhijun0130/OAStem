/**
 * BIZ-OPT-09 前端回归：建档完整度看板 + 导入「部分成功」对话框。
 *
 * 覆盖的是后端单测与接口用例都看不到的两件事：
 *  1) 看板卡片是否真的渲染出来、点击是否打开清单（接口 200 不代表页面能用）；
 *  2) 部分成功时对话框是否留在页面上并把「成功 N 条 / 失败 M 行」摊开——
 *     如果只弹一句「导入完成」就关窗，HR 会当全部成功，漏掉的人等发工资才发现。
 */
const path = require('path')
const fs = require('fs')
const { execFileSync } = require('child_process')
const { chromium } = (() => {
  try { return require('playwright-core') } catch (e) {
    return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core')
  }
})()

const PY = 'C:/Users/qianz/.workbuddy/binaries/python/envs/default/Scripts/python.exe'
const SEED = path.join(__dirname, '..', '..', 'api', 'system', 'seed_user_import_fixture.py')
const BASE = 'http://127.0.0.1:3000'
const R = []
const ck = (n, ok, d = '') => { R.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} | ${n} | ${d}`) }

const seed = (arg) => execFileSync(PY, arg ? [SEED, arg] : [SEED], { encoding: 'utf8', timeout: 120000 }).trim()

;(async () => {
  const xlsx = seed()
  ck('夹具：导入文件已生成', xlsx.endsWith('.xlsx') && fs.existsSync(xlsx), xlsx)

  const b = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 950 }, deviceScaleFactor: 2 })).newPage()
  const errs = []
  p.on('pageerror', (e) => errs.push('pageerror: ' + e.message))
  p.on('console', (m) => { if (m.type() === 'error') errs.push('console: ' + m.text()) })

  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  const tok = await p.evaluate(async () => {
    const r = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'Admin@123456' }),
    })
    return (await r.json()).data.token
  })
  await p.evaluate((t) => localStorage.setItem('oa_token', t), tok)

  // ---------------- 1. 建档完整度看板 ----------------
  console.log('== 1. 建档完整度看板 ==')
  await p.goto(BASE + '/hr/employee', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1600)

  ck('页面出现「建档完整度」区块', (await p.locator('.completeness__title').count()) === 1,
    String(await p.locator('.completeness__title').count()))

  const cards = p.locator('.completeness__card')
  ck('六张维度卡片齐全', (await cards.count()) === 6, String(await cards.count()))

  const labels = ['无身份证', '无在职合同', '无参加工作日期', '无直属上级', '无薪资档案', '无银行账户']
  const cardText = (await p.locator('.completeness__cards').innerText()).replace(/\s+/g, ' ')
  ck('六个维度名称齐全', labels.every((n) => cardText.includes(n)), cardText.slice(0, 120))
  ck('卡片显示人数与占比', /占 \d+(\.\d+)?%/.test(cardText), cardText.slice(0, 120))

  const sub = await p.locator('.completeness__sub').innerText()
  ck('显示在职人数分母', /在职 \d+ 人/.test(sub.replace(/\s+/g, ' ')), sub)

  // 员工列表本身不能被新增的卡片挤坏
  ck('下方员工花名册表格仍在', (await p.locator('.el-table').count()) >= 1, '')

  // 点一张有人数的卡片 → 打开清单
  const target = cards.filter({ hasText: '无在职合同' }).first()
  await target.click()
  await p.waitForTimeout(900)
  const dlg = p.locator('.el-dialog:visible')
  ck('点击卡片打开清单弹窗', (await dlg.count()) >= 1, String(await dlg.count()))

  const dlgText = (await dlg.first().innerText()).replace(/\s+/g, ' ')
  ck('弹窗标题带维度名', dlgText.includes('建档完整度 - 无在职合同'), dlgText.slice(0, 90))
  ck('弹窗点明「只读」与补录位置', dlgText.includes('只读') || dlgText.includes('待补'), '')
  ck('弹窗有导出按钮', (await p.locator('.el-dialog:visible button:has-text("导出")').count()) >= 1, '')

  const heads = await p.locator('.el-dialog:visible .el-table__header th').allInnerTexts()
  const flatHeads = heads.map((h) => h.trim()).filter(Boolean)
  ck('清单六列表头齐全',
    ['工号', '姓名', '部门', '任职状态', '入职日期', '直属上级'].every((h) => flatHeads.includes(h)),
    flatHeads.join('|'))

  const rows = await p.locator('.el-dialog:visible .el-table__body tbody tr').count()
  ck('清单有数据行', rows > 0, String(rows))
  await p.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'completeness.png') })
  await p.keyboard.press('Escape')
  await p.waitForTimeout(500)

  // ---------------- 2. 导入：部分成功对话框 ----------------
  console.log('== 2. 导入部分成功对话框 ==')
  await p.goto(BASE + '/system/user', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)

  const importBtn = p.locator('button:has-text("批量导入")').first()
  ck('存在「批量导入」入口', (await importBtn.count()) >= 1, '')
  await importBtn.click()
  await p.waitForTimeout(600)

  const importDlg = p.locator('.el-dialog:visible')
  ck('导入弹窗打开', (await importDlg.count()) >= 1, '')
  ck('弹窗有「下载模板」', (await p.locator('.el-dialog:visible button:has-text("下载模板")').count()) >= 1, '')

  await p.locator('.el-dialog:visible input[type="file"]').setInputFiles(xlsx)
  await p.waitForTimeout(600)
  await p.locator('.el-dialog:visible button:has-text("开始导入")').click()
  await p.waitForTimeout(2500)

  const afterText = (await p.locator('.el-dialog:visible').first().innerText()).replace(/\s+/g, ' ')
  ck('导入后弹窗仍留在页面上（未直接关闭）', (await p.locator('.el-dialog:visible').count()) >= 1,
    String(await p.locator('.el-dialog:visible').count()))
  ck('显示成功与失败计数', /成功 1 条/.test(afterText) && /失败 1 行/.test(afterText),
    afterText.slice(0, 160))
  ck('提示已成功的不重复创建', afterText.includes('不会重复创建'), '')
  ck('列出失败行号与原因', /1 行有问题|行号/.test(afterText) || afterText.includes('手机号'), '')

  const errRows = await p.locator('.el-dialog:visible .el-table__body tbody tr').count()
  ck('失败明细表有行', errRows >= 1, String(errRows))
  await p.screenshot({ path: path.join(__dirname, '..', '..', '.runtime', 'import_partial.png') })

  ck('无运行时错误', errs.length === 0, errs.slice(0, 2).join(' | '))

  await b.close()
  seed('clean')
  const fail = R.filter((x) => !x).length
  console.log(`\nSUMMARY: ${R.length - fail}/${R.length} 通过`)
  process.exit(fail ? 1 : 0)
})().catch(async (e) => {
  console.error('EXCEPTION', e.message)
  try { seed('clean') } catch (ignored) { /* 清理失败不掩盖原始异常 */ }
  process.exit(1)
})
