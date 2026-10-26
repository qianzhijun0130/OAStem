const path=require('path'),fs=require('fs')
const {chromium}=(() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE='http://127.0.0.1:3000'
const TOKEN=fs.readFileSync(process.env.OA_TOKEN_FILE || require('path').join(__dirname, '..', '..', '.runtime', 'admin_token.txt'), 'utf8').trim()
const R=[];const ck=(n,ok,d='')=>{R.push({n,ok});console.log(`${ok?'PASS':'FAIL'} | ${n} | ${d}`)}
;(async()=>{
 const b=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true})
 const p=await (await b.newContext({viewport:{width:1440,height:900}})).newPage()
 const errs=[];p.on('pageerror',e=>errs.push('pageerror: '+e.message));p.on('console',m=>{if(m.type()==='error')errs.push(m.text())})
 await p.goto(BASE+'/login',{waitUntil:'networkidle'})
 await p.evaluate(t=>localStorage.setItem('oa_token',t),TOKEN)
 await p.goto(BASE+'/dashboard',{waitUntil:'networkidle'});await p.waitForTimeout(2500)

 const topMenus=(await p.locator('.el-menu--vertical').first().locator(':scope > li').allInnerTexts()).map(s=>s.trim().split('\n')[0])
 ck('顶级菜单顺序', JSON.stringify(topMenus)===JSON.stringify(['工作台','发起流程','任务中心','流程管理','分析中心','公告中心','日志管理','系统管理','人力资源','自助服务']), `actual=${topMenus.join('|')}`)

 const tc=p.locator('.el-sub-menu').filter({hasText:'任务中心'}).first()
 const tcItems=(await tc.locator('.el-menu-item').allInnerTexts()).map(s=>s.trim())
 ck('任务中心子项顺序', JSON.stringify(tcItems)===JSON.stringify(['我的流程','待办任务','已办任务','抄送任务']), `actual=${tcItems.join('|')}`)

 const fm=p.locator('.el-sub-menu').filter({hasText:'流程管理'}).first()
 const fmItems=(await fm.locator('.el-menu-item').allInnerTexts()).map(s=>s.trim())
 ck('流程管理子项顺序', JSON.stringify(fmItems)===JSON.stringify(['流程模板','表单设计','实例监控']), `actual=${fmItems.join('|')}`)

 await p.goto(BASE+'/workflow/cc',{waitUntil:'networkidle'});await p.waitForTimeout(2000)
 const title=await p.locator('.el-radio-button').first().innerText().catch(()=> '')
 ck('抄送任务页可访问且渲染筛选', title.includes('未读'), `title=${title}`)
 const headers=(await p.locator('.el-table__header th').allInnerTexts()).map(s=>s.trim())
 ck('抄送表列完整', ['审批单','流程名称','发起人','所属公司','所属部门','流程状态','当前节点','抄送人','抄送说明','抄送时间','操作'].every(h=>headers.includes(h)), `headers=${headers.join('|')}`)
 await p.screenshot({path:require('path').join(__dirname, '..', '..', '.runtime') + '/menu_cc.png',fullPage:true})

 const bad=errs.filter(e=>/500|Cannot read propert|TypeError|Failed to resolve component/.test(e))
 ck('无 500 / 组件解析失败', bad.length===0, `errs=${bad.slice(0,2).join('||')}`)
 await b.close();const f=R.filter(r=>!r.ok);console.log(`\nSUMMARY: ${R.length-f.length}/${R.length} 通过`);process.exit(f.length?1:0)
})().catch(e=>{console.error('SCRIPT_ERROR',e);process.exit(2)})
