import { describe, expect, it } from 'vitest'
import { LUCIDE_ICON_NAMES, LEGACY_EP_ICONS, resolveIcon } from '@/utils/icons'

/**
 * 图标注册表是「数据库里存图标名、运行时按名解析」的关键一环，
 * 一旦解析失败，菜单/按钮会**静默不显示图标**（不报错），因此这里覆盖三类输入：
 * 新名、历史 EP 名、非法名。
 */
describe('utils/icons', () => {
  it('能解析 Lucide 名', () => {
    expect(resolveIcon('FileText')).toBeTruthy()
    expect(resolveIcon('House')).toBeTruthy()
  })

  it('历史 EP 名回落到对应的 Lucide 图标', () => {
    expect(resolveIcon('Document')).toBe(resolveIcon('FileText'))
    expect(resolveIcon('HomeFilled')).toBe(resolveIcon('House'))
    // 映射表里的每个旧名都必须能真正解析出来，否则菜单会掉图标
    for (const legacyName of Object.keys(LEGACY_EP_ICONS)) {
      expect(resolveIcon(legacyName), `旧图标名 ${legacyName} 解析失败`).toBeTruthy()
    }
  })

  it('无法识别或为空时返回 undefined（调用方据此不渲染）', () => {
    expect(resolveIcon('NoSuchIcon')).toBeUndefined()
    expect(resolveIcon('')).toBeUndefined()
    expect(resolveIcon(undefined)).toBeUndefined()
    expect(resolveIcon(null)).toBeUndefined()
  })

  it('可选图标名列表有序且非空（图标选择器依赖它）', () => {
    expect(LUCIDE_ICON_NAMES.length).toBeGreaterThan(20)
    const sorted = [...LUCIDE_ICON_NAMES].sort()
    expect(LUCIDE_ICON_NAMES).toEqual(sorted)
  })
})
