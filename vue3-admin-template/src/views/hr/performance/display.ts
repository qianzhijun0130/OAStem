/**
 * 绩效页的显示辅助（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>分数一律保留 1 位；等级色按 A/B/C/D 映射，未知等级回落灰色。</p>
 */

/** 分数格式化：null/undefined 显示 `-`，否则保留 1 位小数 */
export const money1 = (value?: number) => (value == null ? '-' : Number(value).toFixed(1))

/** 等级 → el-tag 主题色 */
export function gradeTagType(grade?: string) {
  if (grade === 'A') return 'success'
  if (grade === 'B') return 'primary'
  if (grade === 'C') return 'warning'
  if (grade === 'D') return 'danger'
  return 'info'
}
