<script setup lang="ts">
/**
 * 发起流程 · 流程卡片。
 *
 * <p>图标优先取流程模板配置的 `icon`（Lucide 名）；未配置时按分类兜底一个默认图标，
 * 保证卡片永远有视觉锚点、不会出现空白方块。</p>
 */
import { computed } from 'vue'
import AppIcon from '@/components/icons/AppIcon.vue'
import type { FlowOptionVO } from '@/api/types'

const props = defineProps<{ flow: FlowOptionVO }>()
const emit = defineEmits<{ (e: 'open', flow: FlowOptionVO): void }>()

/** 未配置图标时的兜底：与「流程」语义一致 */
const iconName = computed(() => props.flow.icon || 'FileText')
</script>

<template>
  <button type="button" class="flow-card" @click="emit('open', flow)">
    <span class="flow-card__icon"><AppIcon :name="iconName" :size="22" /></span>
    <span class="flow-card__name">{{ flow.flowName }}</span>
    <span class="flow-card__code">{{ flow.flowCode }}</span>
  </button>
</template>

<style scoped>
.flow-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 132px;
  padding: 16px 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.flow-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 12px rgb(64 158 255 / 16%);
  transform: translateY(-2px);
}

.flow-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #f2f7ff;
  color: var(--el-color-primary);
}

.flow-card__name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  text-align: center;
  line-height: 1.35;
  word-break: break-all;
}

.flow-card__code {
  font-size: 11px;
  color: #c0c4cc;
  transform: scale(0.95);
}
</style>
