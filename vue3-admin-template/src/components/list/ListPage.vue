<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { ElCard } from 'element-plus'

/**
 * 列表页骨架：统一「筛选区 / 工具栏 / 表格 / 分页」四段布局与分页行为。
 *
 * <p><b>职责边界（只做布局与分页）</b></p>
 * <ul>
 *   <li>负责：卡片外框、筛选区与工具栏左右排布、表格的加载态/边框/斑马纹/空状态、底部分页。</li>
 *   <li>不负责：任何业务字段、请求、权限判断、状态标签映射——列定义、筛选表单、行内操作全部由使用方
 *       通过插槽注入，组件对业务零假设。</li>
 * </ul>
 *
 * <p>这样拆分的原因：列表页真正重复的是「布局 + 分页 + 空/加载态」这套壳，而各页的列与筛选差异极大。
 * 把差异留在页面、把共性收进组件，才能既消重又不牺牲灵活性。</p>
 *
 * <p><b>数据流</b>：组件是**受控**的（current-page / page-size 全部由 props 决定），
 * 分页交互只发事件，不自行改页码——与 {@code useListQuery} 配合时，页码的唯一真相源在 composable。</p>
 */

interface Props {
  /** 表格数据；传 null/undefined 会被安全降级为空数组 */
  data?: unknown[] | null
  /** 总条数；非数字或负数按 0 处理 */
  total?: number
  /** 加载态，绑定到表格 v-loading */
  loading?: boolean
  /** 当前页码（从 1 开始） */
  pageNo?: number
  /** 每页条数 */
  pageSize?: number
  /** 可选的每页条数列表 */
  pageSizes?: number[]
  /** 表格是否带边框 */
  border?: boolean
  /** 是否斑马纹 */
  stripe?: boolean
  /** 行主键字段名 */
  rowKey?: string
  /** 空数据文案（未提供 #empty 插槽时生效） */
  emptyText?: string
  /** 是否显示分页器（如「全部导出预览」等不需要分页的场景可关） */
  showPagination?: boolean
  /** 分页器布局 */
  paginationLayout?: string
  /** 是否包裹 el-card（页面已有卡片外壳时传 false） */
  card?: boolean
  /** 卡片阴影（仅 card=true 时生效） */
  cardShadow?: 'always' | 'hover' | 'never'
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  total: 0,
  loading: false,
  pageNo: 1,
  pageSize: 10,
  pageSizes: () => [10, 20, 50],
  border: true,
  stripe: false,
  rowKey: 'id',
  emptyText: '暂无数据',
  showPagination: true,
  paginationLayout: 'total, sizes, prev, pager, next, jumper',
  card: true,
  cardShadow: 'never',
})

const emit = defineEmits<{
  /** 页码变化（供 v-model:page-no 使用） */
  'update:pageNo': [value: number]
  /** 每页条数变化（供 v-model:page-size 使用） */
  'update:pageSize': [value: number]
  /** 每页条数变化（语义事件，便于只监听不双向绑定） */
  sizeChange: [value: number]
  /** 页码变化（语义事件） */
  currentChange: [value: number]
}>()

const slots = useSlots()
const hasFilters = computed(() => Boolean(slots.filters))
const hasToolbar = computed(() => Boolean(slots.toolbar))

/** 容错：接口异常或尚未加载时 data 可能为 null，直接交给 el-table 会报错 */
const rows = computed<unknown[]>(() => (Array.isArray(props.data) ? props.data : []))

/** 容错：total 为 NaN / 负数时归零，避免分页器显示异常 */
const safeTotal = computed(() => (typeof props.total === 'number' && props.total > 0 ? props.total : 0))

function onSizeChange(size: number) {
  emit('update:pageSize', size)
  emit('sizeChange', size)
}

function onCurrentChange(page: number) {
  emit('update:pageNo', page)
  emit('currentChange', page)
}
</script>

<template>
  <component :is="card ? ElCard : 'div'" :shadow="card ? cardShadow : undefined" class="list-page">
    <!-- 筛选区 + 工具栏：任一侧存在才渲染整行，避免空 div 撑出空白 -->
    <div v-if="hasFilters || hasToolbar" class="list-page__head">
      <div v-if="hasFilters" class="list-page__filters">
        <slot name="filters" />
      </div>
      <div v-if="hasToolbar" class="list-page__toolbar">
        <slot name="toolbar" />
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      :border="border"
      :stripe="stripe"
      :row-key="rowKey"
      class="list-page__table"
    >
      <!-- 列定义由使用方注入，组件不假设任何业务字段 -->
      <slot />
      <template #empty>
        <slot name="empty">
          <el-empty :description="emptyText" :image-size="70" />
        </slot>
      </template>
    </el-table>

    <el-pagination
      v-if="showPagination"
      :current-page="pageNo"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="safeTotal"
      :layout="paginationLayout"
      background
      class="list-page__pagination"
      @size-change="onSizeChange"
      @current-change="onCurrentChange"
    />
  </component>
</template>

<style scoped>
.list-page__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.list-page__filters {
  flex: 1 1 auto;
  min-width: 0;
}

.list-page__toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-page__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
