import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'
import { logAsyncError } from './utils/asyncGuard'
import './permission'
import './styles/index.css'

const app = createApp(App)

/**
 * 全局兜底（与 `utils/asyncGuard.ts` 的页面级约定互补）。
 *
 * - `errorHandler`：接管组件渲染 / 生命周期 / **模板事件处理器**抛出的异常。
 *   Vue 在开发模式下对未被接管的错误会**重新抛出**（便于发现），配置了 errorHandler
 *   后则由此处统一记录，不再升级为控制台的未捕获异常。
 * - `unhandledrejection`：兜住任何仍未 catch 的 Promise rejection（最后一道防线）。
 *
 * 注意：这里只「记录」，不改变业务语义——各页面仍应就近 catch（见 asyncGuard.ts 约定），
 * 以便做 loading 复位、降级渲染等处理。用户可读的提示统一由请求拦截器负责。
 */
app.config.errorHandler = (error, _instance, info) => {
  logAsyncError(`组件异常（${info}）`, error)
}

window.addEventListener('unhandledrejection', (event) => {
  logAsyncError('未捕获的 Promise rejection', event.reason)
  // 标记为已处理：避免浏览器再打一遍「Uncaught (in promise)」，
  // 也让自动化测试的 pageerror 断言不被「已兜底」的异常污染
  event.preventDefault()
})

// 全局注册图标：后端 oa_menu.icon 存图标名（如 HomeFilled），侧边栏通过 <component :is> 动态渲染
for (const [name, component] of Object.entries(ElementPlusIcons)) {
  app.component(name, component)
}

app.directive('permission', permission)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
