<script setup lang="ts">
import { computed } from 'vue'
import { resolveIcon } from '@/utils/icons'

/**
 * 统一图标组件：按名字渲染 Lucide SVG 图标。
 *
 * <p>对外只暴露 name/size/strokeWidth 三个属性，颜色继承 `currentColor`（跟随文字色），
 * 因此按钮、菜单、表格里的着色都无需额外传 color。</p>
 *
 * <p>名字无法识别时**不渲染任何东西**（而不是渲染一个空方框），
 * 避免老数据里写了不存在的图标名时页面出现诡异占位。</p>
 */
const props = withDefaults(
  defineProps<{
    /** 图标名：Lucide 名（FileText）或历史 EP 名（Document） */
    name?: string | null
    /** 尺寸（px） */
    size?: number | string
    /** 线宽，Lucide 默认 2 */
    strokeWidth?: number
  }>(),
  { name: '', size: 16, strokeWidth: 2 },
)

const icon = computed(() => resolveIcon(props.name))
</script>

<template>
  <component
    :is="icon"
    v-if="icon"
    :size="size"
    :stroke-width="strokeWidth"
    class="app-icon"
    aria-hidden="true"
  />
</template>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: middle;
  flex: 0 0 auto;
}
</style>
