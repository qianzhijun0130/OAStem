<script setup lang="ts">
/** 我的发起：本人提交的审批单，支持查看详情与撤销（仅审批中）。 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ApprovalDetailDrawer from '../components/ApprovalDetailDrawer.vue'
import { getMineApi, withdrawApprovalApi } from '@/api/approval'
import type { ApprovalInstanceVO } from '@/api/types'

/** 1草稿 2审批中 3已通过 4已驳回 5已撤销 */
const STATUS_IN_PROGRESS = 2

const loading = ref(false)
const list = ref<ApprovalInstanceVO[]>([])
const total = ref(0)
const query = reactive<{ pageNo: number; pageSize: number; status?: number }>({
  pageNo: 1,
  pageSize: 10,
})

const detailVisible = ref(false)
const detailId = ref<number>()

function statusTagType(status?: number): 'success' | 'info' | 'danger' | 'warning' {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 5) return 'info'
  return 'warning'
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getMineApi(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.status = undefined
  query.pageNo = 1
  void loadPage()
}

function showDetail(row: ApprovalInstanceVO) {
  detailId.value = row.id
  detailVisible.value = true
}

async function withdraw(row: ApprovalInstanceVO) {
  try {
    await ElMessageBox.confirm(`确定撤销「${row.title}」吗？撤销后审批流程立即终止。`, '撤销确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  await withdrawApprovalApi(row.id)
  ElMessage.success('已撤销')
  await loadPage()
}

onMounted(loadPage)
</script>

<template>
  <div class="mine">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="loadPage">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" :value="1" />
            <el-option label="审批中" :value="2" />
            <el-option label="已通过" :value="3" />
            <el-option label="已驳回" :value="4" />
            <el-option label="已撤销" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPage">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="title" label="申请标题" min-width="200" />
        <el-table-column prop="flowName" label="流程名称" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNode" label="当前节点" width="150">
          <template #default="{ row }">{{ row.currentNode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="发起时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === STATUS_IN_PROGRESS"
              v-permission="'system:approval:withdraw'"
              link
              type="danger"
              @click="withdraw(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无发起记录" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="mine__pagination"
      />
    </el-card>

    <ApprovalDetailDrawer v-model:visible="detailVisible" :instance-id="detailId" />
  </div>
</template>

<style scoped>
.mine__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
