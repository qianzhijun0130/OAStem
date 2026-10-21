<script setup lang="ts">
/**
 * ResponsiveDialog —— 响应式弹窗封装
 *
 * 为什么封装：原系统 18 处 el-dialog 写死像素宽度（480~720px），
 * 在窄屏（< 弹窗宽度）会横向溢出，确定/取消按钮被挤出可视区、点不到。
 * 这里桌面端保持传入的 width，窄屏通过 max-width 自动收缩，保证"完整展示"。
 */
interface Props {
  modelValue: boolean
  title?: string
  /** 桌面端目标宽度，如 '520px' / '90%'，窄屏会被 max-width 兜底 */
  width?: string
  /** 弹窗距顶部距离，透传 el-dialog 的 top */
  top?: string
  closeOnClickModal?: boolean
  destroyOnClose?: boolean
  appendToBody?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  title: '',
  width: '520px',
  top: '15vh',
  closeOnClickModal: true,
  destroyOnClose: false,
  appendToBody: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

function handleUpdate(value: boolean) {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    :top="top"
    :close-on-click-modal="closeOnClickModal"
    :destroy-on-close="destroyOnClose"
    :append-to-body="appendToBody"
    class="oa-responsive-dialog"
    @update:model-value="handleUpdate"
  >
    <slot />
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<style scoped>
/* 窄屏兜底：弹窗宽度不得超过视口，左右各留 16px，避免横向溢出 */
.oa-responsive-dialog {
  max-width: calc(100vw - 32px);
}
</style>
