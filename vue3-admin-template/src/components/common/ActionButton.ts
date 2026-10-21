import { defineComponent, h, type VNode } from 'vue'
import { ElButton } from 'element-plus'
import AppIcon from '@/components/icons/AppIcon.vue'
import { actionIcon } from '@/utils/actionIcons'

/**
 * 全局 `el-button` 包装：按按钮文字自动配 Lucide 图标，并补上悬停提示。
 *
 * <p><b>为什么用全局覆盖而不是逐个改造</b>：全站 160+ 个按钮散落在 20 多个页面，
 * 逐个 import 图标组件既啰嗦又容易漏、风格还会漂移；在 main.ts 里把 `ElButton`
 * 注册成本组件后，一律生效、新增页面也自动继承。</p>
 *
 * <p><b>为什么不包 el-tooltip</b>：Tooltip 会把按钮变成「组件的非元素根节点」，
 * 挂在按钮上的 `v-permission` 等指令将失效（权限按钮可能错误显示）。改用原生
 * `title` 提示，DOM 结构完全不变、指令照常工作；需要更精致浮层的场景
 * （如登录页图标）再单独用 `el-tooltip`。</p>
 *
 * <p><b>不覆盖已有图标</b>：按钮自带 `icon` 属性或 `#icon` 插槽时保持原样，
 * 避免出现两个图标。</p>
 */
function plainText(nodes: unknown): string {
  let out = ''
  const walk = (node: unknown): void => {
    if (node == null || typeof node === 'boolean') {
      return
    }
    if (typeof node === 'string' || typeof node === 'number') {
      out += String(node)
      return
    }
    if (Array.isArray(node)) {
      node.forEach(walk)
      return
    }
    const vnode = node as VNode
    if (vnode.children == null) {
      return
    }
    if (typeof vnode.children === 'string') {
      out += vnode.children
      return
    }
    if (Array.isArray(vnode.children)) {
      walk(vnode.children)
    }
  }
  walk(nodes)
  return out.replace(/\s+/g, ' ').trim()
}

export default defineComponent({
  name: 'ElButton',
  inheritAttrs: false,
  setup(_props, { slots, attrs }) {
    return () => {
      const children = (slots.default ? slots.default() : []) as VNode[]
      const text = plainText(children)
      // icon-only：操作列只显示图标（文字仍用于 title/aria-label），文字类按钮保持原样
      const props: Record<string, unknown> = { ...attrs }
      const iconOnly = props['icon-only'] != null || props.iconOnly != null
      delete props['icon-only']
      delete props.iconOnly

      const hasOwnIcon = props.icon != null || !!slots.icon
      const iconName = hasOwnIcon ? undefined : actionIcon(text)
      const keepText = !iconOnly || !iconName
      const iconNode = iconName
        ? h(AppIcon, { name: iconName, size: iconOnly ? 18 : 14, class: 'action-btn__icon' })
        : null
      const content = iconNode
        ? keepText
          ? [iconNode, ...children]
          : [iconNode]
        : children

      // 原生 title 作为悬停提示：文案即提示，无需额外浮层也不会破坏指令（也不会抢 Tab 焦点）
      if (iconName && props.title == null) {
        props.title = text
      }
      if (iconOnly && iconName) {
        props['aria-label'] = text
        props.class = ['action-btn--icon-only', props.class]
      }
      return h(ElButton, props, {
        default: () => content,
        icon: slots.icon,
        loading: slots.loading,
      })
    }
  },
})
