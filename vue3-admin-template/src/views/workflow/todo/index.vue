<script setup lang="ts">
/**
 * 我的待办：处理 Flowable 中挂载给当前用户的候选人任务（同意 / 驳回 / 转办）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ApprovalDetailDrawer from '../components/ApprovalDetailDrawer.vue'
import { completeApprovalApi, getTodoApi, transferApprovalApi } from '@/api/approval'
import { getUserCandidatesApi } from '@/api/user'
import type { ApprovalTodoVO, UserVO } from '@/api/types'

const loading = ref(false)
const list = ref<ApprovalTodoVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

const detailVisible = ref(false)
const detailId = ref<number>()

/** 审批对话框 */
const dialogVisible = ref(false)
const dialogAction = ref<'approve' | 'reject'>('approve')
const opinion = ref('')
const submitting = ref(false)
const currentTask = ref<ApprovalTodoVO | null>(null)

/** 转办对话框 */
const transferVisible = ref(false)
const transferTarget = ref<number>()
const userOptions = ref<UserVO[]>([])

async function loadPage() {
  loading.value = true
  try {
    const res = await getTodoApi(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSizeChange(size: number) {
  query.pageSize = size
  void loadPage()
}

function handlePageChange(page: number) {
  query.pageNo = page
  void loadPage()
}

async function loadUsers() {
  const res = await getUserCandidatesApi()
  userOptions.value = res
}

function openApprove(row: ApprovalTodoVO, action: 'approve' | 'reject') {
  currentTask.value = row
  dialogAction.value = action
  opinion.value = ''
  dialogVisible.value = true
}

async function submitApprove() {
  if (!currentTask.value) {
    return
  }
  if (dialogAction.value === 'reject' && !opinion.value.trim()) {
    ElMessage.warning('驳回时请填写审批意见')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定${dialogAction.value === 'approve' ? '同意' : '驳回'}「${currentTask.value.title}」吗？`,
      '审批确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await completeApprovalApi({
      taskId: currentTask.value.taskId,
      action: dialogAction.value,
      opinion: opinion.value.trim(),
    })
    ElMessage.success(dialogAction.value === 'approve' ? '已同意' : '已驳回')
    dialogVisible.value = false
    await loadPage()
  } finally {
    submitting.value = false
  }
}

function openTransfer(row: ApprovalTodoVO) {
  currentTask.value = row
  transferTarget.value = undefined
  transferVisible.value = true
}

async function submitTransfer() {
  if (!currentTask.value || !transferTarget.value) {
    ElMessage.warning('请选择转办对象')
    return
  }
  await transferApprovalApi({
    taskId: currentTask.value.taskId,
    targetUserId: transferTarget.value,
  })
  ElMessage.success('已转办')
  transferVisible.value = false
  await loadPage()
}

function showDetail(row: ApprovalTodoVO) {
  detailId.value = row.instanceId
  detailVisible.value = true
}

onMounted(async () => {
  await loadUsers()
  await loadPage()
})
</script>

<template>
  <div class="todo">
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="title" label="申请标题" min-width="200" />
        <el-table-column prop="flowName" label="流程名称" width="140" />
        <el-table-column prop="applicantName" label="发起人" width="110" />
        <el-table-column prop="currentNode" label="当前节点" width="150" />
        <el-table-column prop="createTime" label="到达时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button icon-only link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button icon-only
              v-permission="'system:approval:complete'"
              link
              type="success"
              @click="openApprove(row, 'approve')"
            >
              同意
            </el-button>
            <el-button icon-only
              v-permission="'system:approval:complete'"
              link
              type="danger"
              @click="openApprove(row, 'reject')"
            >
              驳回
            </el-button>
            <el-button icon-only
              v-permission="'system:approval:transfer'"
              link
              type="warning"
              @click="openTransfer(row)"
            >
              转办
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待办事项" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="todo__pagination"
      />
    </el-card>

    <ResponsiveDialog v-model="dialogVisible" :title="dialogAction === 'approve' ? '审批同意' : '审批驳回'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="申请标题">
          <span>{{ currentTask?.title }}</span>
        </el-form-item>
        <el-form-item label="当前节点">
          <span>{{ currentTask?.currentNode }}</span>
        </el-form-item>
        <el-form-item label="审批意见" :required="dialogAction === 'reject'">
          <el-input
            v-model="opinion"
            type="textarea"
            :rows="3"
            placeholder="请填写审批意见"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="dialogAction === 'approve' ? 'success' : 'danger'"
          :loading="submitting"
          @click="submitApprove"
        >
          确定{{ dialogAction === 'approve' ? '同意' : '驳回' }}
        </el-button>
      </template>
    </ResponsiveDialog>

    <ResponsiveDialog v-model="transferVisible" title="转办任务" width="420px">
      <el-form label-width="90px">
        <el-form-item label="申请标题">
          <span>{{ currentTask?.title }}</span>
        </el-form-item>
        <el-form-item label="转办给" required>
          <el-select v-model="transferTarget" filterable placeholder="请选择人员" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确定转办</el-button>
      </template>
    </ResponsiveDialog>

    <ApprovalDetailDrawer v-model:visible="detailVisible" :instance-id="detailId" @done="loadPage" />
  </div>
</template>

<style scoped>
.todo__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
