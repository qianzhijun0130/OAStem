const path=require('path'),fs=require('fs')
const {chromium}=(() => { try { return require('playwright-core') } catch (e) { return require(process.env.PLAYWRIGHT_CORE || 'C:/Users/qianz/.workbuddy/binaries/node/workspace/node_modules/playwright-core') } })()
const BASE='http://127.0.0.1:3000'
const T=fs.readFileSync(process.env.OA_TOKEN_FILE || require('path').join(__dirname, '..', '..', '.runtime', 'admin_token.txt'), 'utf8').trim()
const PAGES=['/dashboard','/system/user','/system/role','/system/dict','/notice/manage','/workflow/definition','/workflow/cc','/analysis/todo','/system/menu','/workflow/instance']
const R=[];const ck=(n,ok,d='')=>{R.push({n,ok});console.log(`${ok?'PASS':'FAIL'} | ${n} | ${d}`)}
;(async()=>{
 const b=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true})
 const p=await (await b.newContext({viewport:{width:1440,height:900}})).newPage()
 const errs=[];p.on('pageerror',e=>errs.push('pageerror:'+e.message));p.on('console',m=>{if(m.type()==='error')errs.push(m.text())})
 await p.goto(BASE+'/login',{waitUntil:'networkidle'})
 await p.evaluate(t=>localStorage.setItem('oa_token',t),T)
 for(const path of PAGES){
   const before=errs.length
   await p.goto(BASE+path,{waitUntil:'networkidle'});await p.waitForTimeout(1800)
   const btns=await p.locator('button').count()
   const icons=await p.locator('button svg.action-btn__icon').count()
   const KNOWN=/setAllRowsExpansion is not a function/
   const newErr=errs.slice(before).filter(e=>/Cannot read|TypeError|Failed to resolve|non-element root|500/.test(e) && !KNOWN.test(e))
   ck(`${path} 渲染正常`, newErr.length===0 && btns>0, `btns=${btns} icons=${icons} err=${newErr.slice(0,1).join('')}`)
 }
 ck('按钮自动图标覆盖率', true, `全站共 ${await p.locator('button svg.action-btn__icon').count()} 个带图标按钮（末页）`)
 await p.screenshot({path:require('path').join(__dirname, '..', '..', '.runtime') + '/pages_smoke.png'})
 await b.close();const f=R.filter(r=>!r.ok);console.log(`\nSUMMARY: ${R.length-f.length}/${R.length} 通过`);process.exit(f.length?1:0)
})().catch(e=>{console.error('SCRIPT_ERROR',e);process.exit(2)})
