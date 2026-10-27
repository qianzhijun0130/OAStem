/**
 * 薪酬页的显示辅助（FE-OPT-06 抽出）。
 *
 * <p>金额、折算系数、状态标签三处在 tab 与弹窗里都要用，放在页面里会逼着子组件
 * 层层透传；它们只做展示不做请求，提到独立模块最省事。</p>
 */

/** 金额：空值显示「-」，避免把 null 渲染成 0 误导人 */
export const money = (value?: number) => (value == null ? '-' : `¥${Number(value).toFixed(2)}`)

/** 折算系数：1 或空表示当月未折算，显示「—」避免整列都是 1.0000 干扰阅读 */
export const prorationText = (value?: number) =>
  (value == null || Number(value) >= 1 ? '—' : Number(value).toFixed(4))

/** 薪资记录状态 → 文案与标签色 */
export const statusMeta = (status: number) => {
  if (status === 4) {
    return { label: '已发放', type: 'success' as const }
  }
  if (status === 3) {
    return { label: '已封账', type: 'warning' as const }
  }
  if (status === 2) {
    return { label: '已复核', type: 'primary' as const }
  }
  return { label: '草稿', type: 'info' as const }
}
