<script setup lang="ts">
/**
 * 发起流程 · 左侧分类导航。
 *
 * <p>纯展示组件：只接收「分类 + 数量 + 当前激活项」，点击时向外抛分类编码。
 * 分类完全来自字典 + 流程数据，因此**新增分类不需要改这个文件**。</p>
 */
defineProps<{
  /** 分类列表：code 用于锚点定位，label 展示，count 展示数量 */
  categories: Array<{ code: string; label: string; count: number }>
  /** 当前可视区域对应的分类编码 */
  active?: string
}>()

const emit = defineEmits<{ (e: 'select', code: string): void }>()
</script>

<template>
  <nav class="apply-nav">
    <div class="apply-nav__title">流程分类</div>
    <ul class="apply-nav__list">
      <li
        v-for="cat in categories"
        :key="cat.code"
        class="apply-nav__item"
        :class="{ 'is-active': cat.code === active }"
        @click="emit('select', cat.code)"
      >
        <span class="apply-nav__label">{{ cat.label }}</span>
        <span class="apply-nav__count">{{ cat.count }}</span>
      </li>
    </ul>
    <div v-if="!categories.length" class="apply-nav__empty">暂无分类</div>
  </nav>
</template>

<style scoped>
.apply-nav {
  position: sticky;
  top: 12px;
  padding: 12px 8px;
  background: #fff;
  border-radius: 6px;
}

.apply-nav__title {
  padding: 0 8px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #909399;
}

.apply-nav__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.apply-nav__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  margin-bottom: 2px;
  border-radius: 4px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.apply-nav__item:hover {
  background: #f4f8ff;
  color: var(--el-color-primary);
}

.apply-nav__item.is-active {
  background: #e8f0ff;
  color: var(--el-color-primary);
  font-weight: 600;
}

.apply-nav__count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: 9px;
  background: #f0f2f5;
  color: #909399;
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  text-align: center;
}

.apply-nav__item.is-active .apply-nav__count {
  background: var(--el-color-primary);
  color: #fff;
}

.apply-nav__empty {
  padding: 8px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
