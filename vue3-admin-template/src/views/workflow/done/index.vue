<script setup lang="ts">
/** 我的已办：本人处理过的审批动作记录。 */
import { onMounted, reactive, ref } from 'vue'
import { getDoneApi } from '@/api/approval'
import type { ApprovalRecordVO } from '@/api/types'

const loading = ref(false)
const list = ref<ApprovalRecordVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

function actionTagType(action?: string): 'success' | 'danger' | 'warning' | 'info' {
  if (action === 'approve') return 'success'
  if (action === 'reject') return 'danger'
  if (action === 'transfer') return 'warning'
  return 'info'
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getDoneApi(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <div class="done">
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="title" label="申请标题" min-width="200" />
        <el-table-column prop="flowName" label="流程名称" width="140" />
        <el-table-column prop="nodeName" label="审批节点" width="150" />
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)">{{ row.actionName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="opinion" label="审批意见" min-width="180">
          <template #default="{ row }">{{ row.opinion || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="处理时间" width="180" />
        <template #empty>
          <el-empty description="暂无已办记录" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="done__pagination"
      />
    </el-card>
  </div>
</template>

<style scoped>
.done__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
