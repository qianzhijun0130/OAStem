<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ListPage from '@/components/list/ListPage.vue'
import PolicyFormDialog from '../dialogs/PolicyFormDialog.vue'
import { leaveTypeLabel } from '../constants'
import { useLeavePolicy } from '../composables/useLeavePolicy'
import type { LeavePolicyVO } from '@/api/types'

/**
 * 「假别政策」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：列表（不分页，后端返回全量）、筛选、新增/编辑弹窗、删除都在本组件内。</p>
 */
const { list, loading, query, search, refresh, load, remove } = useLeavePolicy()

onMounted(load)

const formRef = ref<InstanceType<typeof PolicyFormDialog> | null>(null)

function openCreate() {
  formRef.value?.openCreate({ city: query.city, year: query.year })
}

function openEdit(row: LeavePolicyVO) {
  formRef.value?.openEdit(row)
}
</script>

<template>
  <ListPage :data="list" :loading="loading" :card="false" :show-pagination="false" empty-text="暂无假别政策">
    <template #filters>
      <div class="hr-leave__bar">
        <el-date-picker
          v-model="query.year"
          type="year"
          value-format="YYYY"
          :clearable="false"
          placeholder="年度"
          style="width: 120px"
        />
        <el-input v-model="query.city" placeholder="城市" clearable style="width: 160px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </template>
    <template #toolbar>
      <el-button v-permission="'hr:leave:policy'" type="primary" data-action="create" @click="openCreate">新增政策</el-button>
    </template>

    <el-table-column prop="city" label="城市" width="120" />
    <el-table-column label="假别" width="120">
      <template #default="{ row }">{{ row.leaveTypeLabel || leaveTypeLabel(row.leaveType) }}</template>
    </el-table-column>
    <el-table-column prop="days" label="法定天数" width="100" />
    <el-table-column prop="effectiveYear" label="生效年度" width="100" />
    <el-table-column label="依据" min-width="220" show-overflow-tooltip>
      <template #default="{ row }">{{ row.remark || '-' }}</template>
    </el-table-column>
    <el-table-column prop="updateTime" label="更新时间" width="170" />
    <el-table-column label="操作" width="150" fixed="right">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:leave:policy'"
          icon-only
          link
          type="primary"
          title="编辑"
          data-action="edit"
          @click="openEdit(row)"
        >
          编辑
        </el-button>
        <el-button
          v-permission="'hr:leave:policy'"
          icon-only
          link
          type="danger"
          title="删除"
          data-action="delete"
          @click="remove(row)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </ListPage>

  <PolicyFormDialog ref="formRef" @saved="refresh" />
</template>
