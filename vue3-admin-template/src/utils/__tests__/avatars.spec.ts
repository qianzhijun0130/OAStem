import { describe, expect, it } from 'vitest'
import { BUILTIN_AVATAR_COUNT, BUILTIN_AVATARS, isBuiltinAvatar } from '@/utils/avatars'

/**
 * 内置头像注册表与后端 `BuiltinAvatars` 是**双份白名单**：
 * 数量或命名对不上，就会出现「界面能选、保存被后端拒绝」或「存了却选不到」。
 * 这里锁定前端一侧的契约（数量、命名格式、唯一性、判定函数）。
 */
describe('utils/avatars', () => {
  it('数量与常量一致且非空', () => {
    expect(BUILTIN_AVATAR_COUNT).toBeGreaterThan(0)
    expect(BUILTIN_AVATARS).toHaveLength(BUILTIN_AVATAR_COUNT)
  })

  it('URL 命名符合 /avatars/avatar-NN.svg 的两位编号格式', () => {
    for (const item of BUILTIN_AVATARS) {
      expect(item.url).toMatch(/^\/avatars\/avatar-\d{2}\.svg$/)
    }
    expect(BUILTIN_AVATARS[0].url).toBe('/avatars/avatar-01.svg')
    expect(BUILTIN_AVATARS[BUILTIN_AVATARS.length - 1].url).toBe(
      `/avatars/avatar-${String(BUILTIN_AVATAR_COUNT).padStart(2, '0')}.svg`,
    )
  })

  it('id 与 name 不重复', () => {
    const ids = new Set(BUILTIN_AVATARS.map((item) => item.id))
    const names = new Set(BUILTIN_AVATARS.map((item) => item.name))
    const urls = new Set(BUILTIN_AVATARS.map((item) => item.url))
    expect(ids.size).toBe(BUILTIN_AVATARS.length)
    expect(names.size).toBe(BUILTIN_AVATARS.length)
    expect(urls.size).toBe(BUILTIN_AVATARS.length)
  })

  it('isBuiltinAvatar 只认登记过的地址', () => {
    expect(isBuiltinAvatar('/avatars/avatar-01.svg')).toBe(true)
    expect(isBuiltinAvatar(`/avatars/avatar-${String(BUILTIN_AVATAR_COUNT).padStart(2, '0')}.svg`)).toBe(true)
    // 越界编号、上传目录、外部地址、空值一律不算内置
    expect(isBuiltinAvatar('/avatars/avatar-99.svg')).toBe(false)
    expect(isBuiltinAvatar('/uploads/2026/10/a.png')).toBe(false)
    expect(isBuiltinAvatar('https://example.com/a.png')).toBe(false)
    expect(isBuiltinAvatar('')).toBe(false)
    expect(isBuiltinAvatar(null)).toBe(false)
    expect(isBuiltinAvatar(undefined)).toBe(false)
  })
})
