import { ref, shallowRef } from 'vue'

/**
 * 「保存不重载」统一控制器。
 *
 * <p>解决什么问题：表单类页面保存成功后，常见写法是 `router.push` 回列表或
 * `load()` 重拉整页数据。前者销毁组件 → 表单内容、滚动位置、Tab 激活、面板展开、
 * 动态增删的条目全部丢失；后者虽不销毁组件，但会把用户翻到的页码重置，
 * 甚至把刚填好的搜索条件冲掉。</p>
 *
 * <p>本组合式函数把「保存动作」与「保存后的反馈」收敛到一处，统一保证：
 * <ul>
 *   <li><b>不跳转</b>：不调用任何路由 API，组件不销毁，页面状态天然保留；</li>
 *   <li><b>不重载</b>：由调用方决定是否重新拉数据；`silent` 模式可完全跳过；</li>
 *   <li><b>失败零副作用</b>：catch 内只记录不改状态，已输入内容原样保留。</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>{@code
 * const saver = useSaveState()
 * await saver.run(() => saveApi(payload), {
 *   label: '保存表单',
 *   onSuccess: () => ElMessage.success('已保存'),
 * })
 * // saver.savedAt  → 最近一次成功时间（用于状态栏显示）
 * // saver.busy     → 是否正在保存（用于按钮 loading）
 * }</pre>
 */
export interface SaveRunOptions {
  /** 动作名称，用于日志与默认文案，如「保存表单」 */
  label: string
  /**
   * 成功回调。
   *
   * <p>典型用途是**局部**刷新受影响的数据（如重新拉取当前页列表）。
   * 传 null 则完全不刷新 —— 适用于设计器这类「内容已在本地、无需回读」的页面。</p>
   */
  onSuccess?: (() => void | Promise<void>) | null
  /** 成功后额外提示；不传则不弹（避免与 onSuccess 里的提示重复） */
  successMessage?: string
  /** 失败时的额外提示；不传则由 request 拦截器统一给出 */
  failMessage?: string
  /** 成功后是否重置控制器状态（如「未保存」标记）。默认 true */
  clearDirtyOnSuccess?: boolean
}

export function useSaveState() {
  /** 是否正在保存，用于按钮 loading 与防重复提交 */
  const busy = ref(false)
  /** 最近一次保存成功时间（`HH:mm:ss`），null 表示本次会话尚未保存成功 */
  const savedAt = shallowRef<string | null>(null)
  /** 是否有未保存改动，由调用方在编辑动作里置 true */
  const dirty = ref(false)

  function markDirty() {
    if (!busy.value) {
      dirty.value = true
    }
  }

  function markClean() {
    dirty.value = false
  }

  /**
   * 执行一次保存。
   *
   * <p>返回是否成功，方便调用方按结果决定后续行为。
   * **失败时不会抛出异常**，也不会改动任何状态（除 busy 复位）——
   * 调用方无需在 catch 里做状态回滚。</p>
   */
  async function run<T>(action: () => Promise<T>, options: SaveRunOptions): Promise<boolean> {
    if (busy.value) {
      return false
    }
    busy.value = true
    try {
      // 成败判定完全依赖「是否抛异常」：
      // request 响应拦截器对业务失败统一 return Promise.reject(...)，对成功则 return body.data；
      // 而启停 / 删除这类 Result<Void> 接口的 body.data 就是 undefined ——
      // 绝不能用返回值真假判成败，否则成功会被误判为失败（这正是本函数第一版的 bug）。
      await action()
      savedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      if (options.clearDirtyOnSuccess !== false) {
        dirty.value = false
      }
      if (options.onSuccess) {
        await options.onSuccess()
      }
      return true
    } catch (error) {
      // 这里只记录，不弹窗、不改状态：用户可读的提示已由 request 拦截器统一给出，
      // 且页面状态完全不动 —— 用户可修正后直接重新提交。
      console.warn(`[useSaveState] ${options.label} 失败`, error)
      return false
    } finally {
      busy.value = false
    }
  }

  /** 回到「未保存」初始态（用于切换表单/模板时） */
  function reset() {
    savedAt.value = null
    dirty.value = false
    busy.value = false
  }

  return { busy, savedAt, dirty, markDirty, markClean, run, reset }
}
