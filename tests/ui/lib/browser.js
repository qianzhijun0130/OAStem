// UI 自动化公共库：浏览器启动、错误收集、断言与汇总。
// 只抽「每个脚本都要写一遍」的部分，不动各脚本的业务断言逻辑。
const path = require('path')
const fs = require('fs')

/** 解析 playwright-core：优先常规 node 解析，失败回退到 PLAYWRIGHT_CORE 指向的绝对路径 */
function playwright() {
  try {
    return require('playwright-core')
  } catch (e) {
    return require(process.env.PLAYWRIGHT_CORE
      || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core')
  }
}

/**
 * 启动浏览器。默认用 Edge（本机已装），可用 OA_BROWSER 指定可执行文件。
 */
async function launch(viewport = { width: 1440, height: 950 }) {
  const { chromium } = playwright()
  const browser = await chromium.launch({
    executablePath: process.env.OA_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: process.env.OA_HEADFUL ? false : true,
  })
  const context = await browser.newContext({ viewport, acceptDownloads: true })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
  page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()) })
  return { browser, context, page, errors }
}

/** 通过接口登录并把 token 写进 localStorage（避免依赖过期的令牌文件） */
async function login(page, baseUrl, username = 'admin', password = 'Admin@123456') {
  await page.goto(baseUrl + '/login', { waitUntil: 'networkidle' })
  const token = await page.evaluate(async ([u, p]) => {
    const r = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: u, password: p }),
    })
    const body = await r.json()
    return body && body.data ? body.data.token : null
  }, [username, password])
  if (token) await page.evaluate((t) => localStorage.setItem('oa_token', t), token)
  return token
}

/** 过滤掉与业务无关的噪声（favicon 404、ResizeObserver 循环告警） */
function realErrors(errors) {
  return errors.filter((e) => !/favicon|ResizeObserver/.test(e))
}

function createReport() {
  const results = []
  const ck = (name, ok, detail = '') => {
    results.push([name, !!ok])
    console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail}`)
    return !!ok
  }
  const summary = () => {
    const failed = results.filter(([, ok]) => !ok)
    console.log(`\nSUMMARY: ${results.length - failed.length}/${results.length} 通过`)
    if (failed.length) console.log('FAILED: ' + failed.map(([n]) => n).join(' ; '))
    return failed.length
  }
  return { ck, summary, results }
}

module.exports = { playwright, launch, login, realErrors, createReport }
