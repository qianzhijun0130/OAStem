/**
 * 系统内置头像注册表。
 *
 * <p><b>与后端必须同步</b>：后端 {@code BuiltinAvatars} 是精确白名单（只认登记过的文件名），
 * 这里多一个或少一个，就会出现「界面能选、保存被拒」或「存了却选不到」。
 * 图片本体在 {@code public/avatars/avatar-NN.svg}，随前端静态资源发布。</p>
 */
export interface BuiltinAvatar {
  id: number
  /** 无障碍名称，同时作为 title 悬停提示 */
  name: string
  /** 站内绝对路径，直接写库（后端按白名单校验） */
  url: string
}

/** 内置头像数量，改这里要同步后端 BuiltinAvatars.COUNT */
export const BUILTIN_AVATAR_COUNT = 12

const URL_PREFIX = '/avatars/'

export const BUILTIN_AVATARS: BuiltinAvatar[] = Array.from(
  { length: BUILTIN_AVATAR_COUNT },
  (_, index) => {
    const no = String(index + 1).padStart(2, '0')
    return { id: index + 1, name: `系统头像 ${no}`, url: `${URL_PREFIX}avatar-${no}.svg` }
  },
)

/** 是否属于内置头像：用于回显选中态（用户自传的头像不在列表里） */
export function isBuiltinAvatar(url?: string | null): boolean {
  return !!url && BUILTIN_AVATARS.some((item) => item.url === url)
}
