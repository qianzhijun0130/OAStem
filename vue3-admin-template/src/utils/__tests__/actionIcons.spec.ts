import { describe, expect, it } from 'vitest'
import { actionIcon } from '@/utils/actionIcons'

/**
 * 「按钮文案 → 图标」是全局按钮组件的唯一推断依据（没有显式 `icon` 时）。
 * 两条规则最容易回归：
 * 1. 规则**按顺序**命中——「确定抄送」必须是勾而不是纸飞机；
 * 2. 文案里可能有全角/半角空白（「登 录」），匹配前要压掉。
 */
describe('utils/actionIcons', () => {
  it('常见操作文案映射到对应图标', () => {
    expect(actionIcon('新增')).toBe('Plus')
    expect(actionIcon('编辑')).toBe('Pencil')
    expect(actionIcon('删除')).toBe('Trash2')
    expect(actionIcon('查询')).toBe('Search')
    expect(actionIcon('导出')).toBe('Download')
  })

  it('去掉文案中的所有空白再匹配', () => {
    expect(actionIcon('登 录')).toBe('LogIn')
    expect(actionIcon('  保 存  ')).toBe('Save')
  })

  it('文案带前后缀也能命中（包含匹配）', () => {
    expect(actionIcon('新建用户')).toBe('Plus')
    expect(actionIcon('批量删除')).toBe('Trash2')
  })

  it('多关键词时「更靠前的规则」胜出：具体动作词压过泛化的确认词', () => {
    // 「修改」规则排在「确定」之前 → 铅笔（而不是勾）
    expect(actionIcon('确认修改')).toBe('Pencil')
    // 「抄送」规则排在「确定」之前 → 纸飞机
    expect(actionIcon('确定抄送')).toBe('Send')
    // 只有单独的确认动作才落到「确定/确认」规则
    expect(actionIcon('确定')).toBe('Check')
    expect(actionIcon('确认')).toBe('Check')
  })

  it('无法识别或空文案返回 undefined（按钮保持纯文字）', () => {
    expect(actionIcon('这是一段普通文字')).toBeUndefined()
    expect(actionIcon('')).toBeUndefined()
  })
})
