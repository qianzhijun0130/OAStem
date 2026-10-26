import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import ResponsiveDialog from './components/ResponsiveDialog.vue'
import ActionButton from './components/common/ActionButton'
import 'element-plus/dist/index.css'

/**
 * FormCreate 低代码表单：渲染器（@form-create/element-ui）+ 可视化设计器（@form-create/designer）。
 *
 * 渲染器用于「发起申请」「审批详情」按规则 JSON 动态渲染表单；设计器用于「表单管理」里拖拽生成规则。
 * 两者都依赖 Element Plus 渲染栈，故必须与 ElementPlus 同时挂载，且**不可**再引入 Element UI（Vue2 栈）。
 *
 * ⚠️ 关键：`@form-create/element-ui` 的默认导出是一个**组件定义对象**
 * （含 name/props/setup），并非 Vue 插件对象，直接 `app.use()` 不会注册全局组件。
 * 那样写不会报错，但模板里的 `<form-create>` 会退化成未知自定义元素 —— 表单区域一片空白，
 * 唯有 pageerror 也捕获不到。必须显式 `app.component()` 注册。
 * 组件真实 props 是 `rule` + `option`（单数），不是 Vue2 时代的 `options`。
 */
import FcRender from '@form-create/element-ui'


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

// 图标统一走 Lucide：由 `components/icons/AppIcon.vue` + `utils/icons.ts` 按名字解析，
// 不再全局注册 Element Plus 图标（那会把 200+ 个图标组件全打进包，且与 Lucide 混用两套风格）。

app.directive('permission', permission)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 全局接管 el-button：按文案自动加 Lucide 图标 + 悬停提示（须在 ElementPlus 之后注册才能覆盖）
app.component('ElButton', ActionButton)

// 响应式弹窗：封装 el-dialog，窄屏自动收缩不溢出（桌面端仍保持传入 width）
app.component('ResponsiveDialog', ResponsiveDialog)

// FormCreate 渲染器：全局注册 <form-create> 组件（导出对象是组件定义，不是 Vue 插件）
app.component('FormCreate', FcRender)
// FormCreate 设计器（@form-create/designer）**故意不在这里注册**：
// 它约 4.8M，全仓只有 system/form/designer.vue 用得到 <fc-designer>。全局注册会把它塞进 entry，
// 让登录页、工作台等所有页面都为首屏多下一大块（实测 entry 2039kB → 摘掉后 1091kB）。
// 改为该页面 onMounted 里动态 import 后运行时安装，见 views/system/form/designer.vue。

app.mount('#app')
