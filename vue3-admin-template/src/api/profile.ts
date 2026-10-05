import { request } from '@/utils/request'
import type { PasswordChangeRequest, ProfileUpdateRequest, ProfileVO } from './types'

/** 当前登录用户的完整资料 */
export function getProfileApi() {
  return request<ProfileVO>({ url: '/profile/info', method: 'get' })
}

/** 修改个人资料（昵称 / 性别 / 手机号 / 邮箱） */
export function updateProfileApi(data: ProfileUpdateRequest) {
  return request<null>({ url: '/profile/update', method: 'put', data })
}

/** 更换头像：avatar 为上传接口返回的相对地址 */
export function updateAvatarApi(avatar: string) {
  return request<null>({ url: '/profile/avatar', method: 'put', data: { avatar } })
}

/** 修改密码，需校验原密码 */
export function changePasswordApi(data: PasswordChangeRequest) {
  return request<null>({ url: '/profile/password', method: 'put', data })
}
