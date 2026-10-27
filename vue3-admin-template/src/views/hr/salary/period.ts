/**
 * 薪资月度的日期口径（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>核算默认「上个月」：当月还没过完，算了也要重算。两者都返回 `yyyy-MM`，
 * 与 `oa_salary_record.month`、`oa_salary_profile.effective_date` 口径一致。</p>
 */

/** 上个月 yyyy-MM */
export function lastMonth(): string {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** 本月 yyyy-MM */
export function currentMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
