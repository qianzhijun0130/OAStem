const path=require('path'),fs=require('fs')
const {chromium}=(() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE='http://127.0.0.1:3000'
const ADMIN=fs.readFileSync(require('path').join(__dirname, '..', '..', '.runtime') + '/ui_admin_token.txt','utf8').trim()
const EMP=fs.readFileSync(require('path').join(__dirname, '..', '..', '.runtime') + '/ui_emp_token.txt','utf8').trim()
const LEADER=fs.readFileSync(require('path').join(__dirname, '..', '..', '.runtime') + '/ui_leader_token.txt','utf8').trim()
const R=[];const ck=(n,ok,d='')=>{R.push({n,ok});console.log(`${ok?'PASS':'FAIL'} | ${n} | ${d}`)}
const EXE='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

async function mkPage(b, tok){
  const ctx=await b.newContext({viewport:{width:1440,height:950}})
  const p=await ctx.newPage()
  const errs=[];p.on('pageerror',e=>errs.push('pageerror:'+e.message))
  p.on('console',m=>{if(m.type()==='error')errs.push('console:'+m.text().slice(0,160))})
  await p.goto(BASE+'/login',{waitUntil:'domcontentloaded'})
  await p.evaluate(t=>localStorage.setItem('oa_token',t),tok)
  return {p,errs}
}

;(async()=>{
 const b=await chromium.launch({executablePath:EXE,headless:true})

 // ============ 1. HR 视角：绩效管理页 ============
 {
  const {p,errs}=await mkPage(b,ADMIN)
  await p.goto(BASE+'/hr/performance',{waitUntil:'networkidle'});await p.waitForTimeout(2200)
  ck('绩效管理页可打开', !p.url().includes('/404'), `url=${p.url()}`)
  const tabs=(await p.locator('.el-tabs__item').allInnerTexts()).map(s=>s.trim())
  ck('页签齐全（考核表/周期/分布/方案/指标库）',
     tabs.some(t=>t.includes('考核表'))&&tabs.some(t=>t.includes('考核周期'))
     &&tabs.some(t=>t.includes('等级分布'))&&tabs.some(t=>t.includes('绩效方案'))
     &&tabs.some(t=>t.includes('指标库')), `tabs=${tabs.join('|')}`)
  const sheetRows=await p.locator('.perf__sheet-table .el-table__body tr').count()
  ck('考核表列表有数据', sheetRows>0, `rows=${sheetRows}`)
  const firstSheetText=(await p.locator('.perf__sheet-table').innerText().catch(()=>''))
  ck('列表显示等级/分数', /A|B|C|D/.test(firstSheetText), firstSheetText.replace(/\s+/g,' ').slice(0,120))

  // 等级分布页签
  await p.locator('.el-tabs__item').filter({hasText:'等级分布'}).click();await p.waitForTimeout(1500)
  const distRows=await p.locator('.el-table__body tr').count()
  ck('等级分布有数据', distRows>0, `rows=${distRows}`)

  // 绩效方案页签
  await p.locator('.el-tabs__item').filter({hasText:'绩效方案'}).click();await p.waitForTimeout(1500)
  const planText=(await p.locator('.el-tab-pane:visible .el-table').first().innerText().catch(()=>''))
  ck('绩效方案列表可见', planText.includes('V-PLAN-001')||planText.includes('验证用绩效方案'),
     planText.replace(/\s+/g,' ').slice(0,120))

  // 指标库页签
  await p.locator('.el-tabs__item').filter({hasText:'指标库'}).click();await p.waitForTimeout(1500)
  const indText=(await p.locator('.el-tab-pane:visible .el-table').first().innerText().catch(()=>''))
  ck('指标库列表可见', indText.includes('测试指标'), indText.replace(/\s+/g,' ').slice(0,120))

  ck('绩效管理页无 JS 报错', errs.filter(e=>!/favicon|Failed to load resource/.test(e)).length===0,
     errs.slice(0,3).join(' || '))
  await p.screenshot({path:require('path').join(__dirname, '..', '..', '.runtime') + '/ui_perf_admin.png',fullPage:false})
  await p.context().close()
 }

 // ============ 2. 员工视角：我的绩效 ============
 {
  const {p,errs}=await mkPage(b,EMP)
  await p.goto(BASE+'/hr/my-performance',{waitUntil:'networkidle'});await p.waitForTimeout(2200)
  ck('我的绩效页可打开', !p.url().includes('/404'), `url=${p.url()}`)
  const bodyText=(await p.locator('body').innerText().catch(()=>''))
  ck('显示考核周期选择', bodyText.includes('考核周期'), '')
  ck('显示最终得分/等级', bodyText.includes('最终得分')||bodyText.includes('等级'),
     bodyText.replace(/\s+/g,' ').slice(0,150))
  const itemRows=await p.locator('.myperf__items .el-table__body tr').count()
  ck('指标明细表有数据', itemRows>0, `rows=${itemRows}`)
  ck('我的绩效页无 JS 报错', errs.filter(e=>!/favicon|Failed to load resource/.test(e)).length===0,
     errs.slice(0,3).join(' || '))
  await p.screenshot({path:require('path').join(__dirname, '..', '..', '.runtime') + '/ui_perf_emp.png',fullPage:false})
  await p.context().close()
 }

 // ============ 3. 主管视角：团队考核表 ============
 {
  const {p,errs}=await mkPage(b,LEADER)
  await p.goto(BASE+'/hr/my-performance',{waitUntil:'networkidle'});await p.waitForTimeout(2200)
  ck('主管可打开我的绩效页', !p.url().includes('/404'), `url=${p.url()}`)
  const t=(await p.locator('body').innerText().catch(()=>''))
  ck('主管页面不出现方案维护入口', !t.includes('新建方案'), '')
  ck('主管页面无 JS 报错', errs.filter(e=>!/favicon|Failed to load resource/.test(e)).length===0,
     errs.slice(0,3).join(' || '))
  await p.context().close()
 }

 await b.close()
 const fail=R.filter(x=>!x.ok).map(x=>x.n)
 console.log(`\nSUMMARY: ${R.length-fail.length}/${R.length} 通过`)
 if(fail.length) console.log('FAILED: '+fail.join(' ; '))
 process.exit(fail.length?1:0)
})().catch(e=>{console.error('ERROR',e);process.exit(2)})
