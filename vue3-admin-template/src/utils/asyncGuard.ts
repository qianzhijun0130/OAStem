/**
 * 异步异常的**统一兜底**约定（对应缺陷 M04-D04 / M05-D05 / M06-D03 / M07-D01 的合并收口）。
 *
 * ## 为什么需要它
 * 请求拦截器（`utils/request.ts`）已经统一弹出了可读的错误提示，但**提示不等于处理**：
 * 若事件处理器或 `onMounted` 里 `await` 的 Promise 被 reject 而调用方没有 `catch`，
 * 它会变成「未捕获的 Promise rejection」——控制台报 `Uncaught (in promise)`、
 * 自动化测试的 `pageerror` 断言随之失败，用户却看到了正确的提示，形成「看起来没问题、测试红」的假象。
 *
 * ## 约定（新增异步处理器时按此执行）
 * 1. 任何在事件处理器 / 生命周期里 `await` 的调用，其所在函数**必须有 `catch`**；
 * 2. `catch` 里只需 `logAsyncError('<业务动作>失败', error)`——用户提示已由拦截器负责，
 *    这里**只做记录**，不再重复弹窗；
 * 3. 需要「失败后仍复位 loading」的加载函数，保留 `finally`，`catch` 写在 `finally` 之前；
 * 4. 全局兜底见 `main.ts`（`unhandledrejection` / `errorHandler`），用于拦截本约定的漏网之鱼。
 *
 * 日志前缀固定为 {@link ASYNC_ERROR_PREFIX}，便于自动化测试区分「已兜底」与「真未捕获」。
 */

/** 兜底日志前缀；自动化测试据此区分「已兜底」与「真未捕获」 */
export const ASYNC_ERROR_PREFIX = '[已被统一兜底]'

/** 记录一个已被兜底的异步错误。 */
export function logAsyncError(label: string, error: unknown): void {
  // eslint-disable-next-line no-console
  console.error(`${ASYNC_ERROR_PREFIX} ${label}`, error)
}
