import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/** 拥有该角色的用户跳过一切权限校验 */
const SUPER_ROLE = 'SUPER_ADMIN'

/**
 * 按钮级权限指令。
 *
 * 用法：
 *   <el-button v-permission="'system:user:add'">新增</el-button>
 *   <el-button v-permission="['system:user:edit', 'system:user:delete']">编辑</el-button>
 *
 * 说明：直接移除 DOM 而非设置 disabled，避免用户通过开发者工具改动样式后越权点击。
 * 真正的防线仍在后端 @PreAuthorize，前端控制只是为了交互体验。
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding: DirectiveBinding<string | string[]>) {
    const required = binding.value
    if (!required || (Array.isArray(required) && required.length === 0)) {
      return
    }
    const userStore = useUserStore()
    if (userStore.roles.includes(SUPER_ROLE)) {
      return
    }
    const codes = Array.isArray(required) ? required : [required]
    const granted = codes.some((code) => userStore.permissions.includes(code))
    if (!granted) {
      el.parentNode?.removeChild(el)
    }
  },
}
