<script setup lang="ts">
/**
 * 保存状态指示条。
 *
 * <p>需求来源：「保存不重载页面，仅通过轻量交互告知用户保存结果」。Toast 会自动消失，
 * 用户切到别的标签页就看不到了；本组件常驻在工具栏右侧，兼具 Toast 的即时性与
 * 状态栏的可回溯性。</p>
 *
 * <p>三种状态：
 * <ul>
 *   <li>无改动 —— 显示「未保存的改动」占位（灰色）；</li>
 *   <li>有改动 —— 显示醒目提示（橙色，带小圆点）；</li>
 *   <li>已保存 —— 显示「已保存 HH:mm:ss」（绿色）。</li>
 * </ul>
 */
withDefaults(defineProps<{ savedAt?: string | null; dirty?: boolean }>(), {
  savedAt: null,
  dirty: false,
})
</script>

<template>
  <span class="save-status" :class="`save-status--${dirty ? 'dirty' : savedAt ? 'saved' : 'idle'}`">
    <i class="save-status__dot" />
    <span v-if="dirty">有未保存的改动</span>
    <span v-else-if="savedAt">已保存 {{ savedAt }}</span>
    <span v-else>尚未保存</span>
  </span>
</template>

<style scoped>
.save-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1;
  color: #909399;
  white-space: nowrap;
}

.save-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c0c4cc;
  flex: 0 0 auto;
}

.save-status--dirty {
  color: #e6a23c;
}

.save-status--dirty .save-status__dot {
  background: #e6a23c;
}

.save-status--saved {
  color: #67c23a;
}

.save-status--saved .save-status__dot {
  background: #67c23a;
}
</style>
