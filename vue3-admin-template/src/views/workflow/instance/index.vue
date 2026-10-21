<script setup lang="ts">
/** 流程实例：管理员视角，展示全部审批单，支持多条件检索与详情/撤销/终止/删除。 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ApprovalDetailDrawer from '../components/ApprovalDetailDrawer.vue'
import {
  getInstancesApi,
  terminateInstanceApi,
  deleteInstanceApi,
  withdrawApprovalApi,
  suspendInstanceApi,
  resumeInstanceApi,
} from '@/api/approval'
import { getFlowOptionsApi } from '@/api/flow'
import type { ApprovalInstanceVO, ApprovalInstancePageQuery, FlowOptionVO } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import ListPage from '@/components/list/ListPage.vue'

const userStore = useUserStore()
const STATUS_IN_PROGRESS = 2
/** 已挂起（阶段四）：可恢复，恢复后回到审批中 */
const STATUS_SUSPENDED = 7

/**
 * 页面私有查询条件。
 *
 * <p>在公共分页查询上补一个 {@code dateRange}：日期选择器天然产出区间数组，
 * 而接口要的是 beginTime/endTime，这层映射由 {@code beforeFetch} 完成。</p>
 */
interface InstanceQuery extends Omit<ApprovalInstancePageQuery, 'pageNo' | 'pageSize'> {
  dateRange?: string[]
}

const flowOptions = ref<FlowOptionVO[]>([])
const detailVisible = ref(false)
const detailId = ref<number>()

// 查询状态（分页/加载态/条件/搜索重置）统一交给 useListQuery，页面只保留业务动作
const {
  list,
  total,
  loading,
  query,
  pageNo,
  pageSize,
  refresh,
  search,
  reset,
  handleSizeChange,
  handlePageChange,
} = useListQuery<ApprovalInstanceVO, InstanceQuery>({
  initialQuery: () => ({
    title: undefined,
    applicantName: undefined,
    status: undefined,
    flowCode: undefined,
    beginTime: undefined,
    endTime: undefined,
    dateRange: undefined,
  }),
  fetcher: (params) => getInstancesApi(params),
  beforeFetch: (q) => {
    q.beginTime = q.dateRange?.[0]
    q.endTime = q.dateRange?.[1]
  },
  onError: (error) => logAsyncError('加载流程实例失败', error),
})

function statusTagType(status?: number): 'success' | 'info' | 'danger' | 'warning' | 'primary' {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 5) return 'info'
  if (status === 6) return 'primary'
  // 已挂起与已撤销同属「非活跃」态，用同一种弱化色
  if (status === STATUS_SUSPENDED) return 'info'
  return 'warning'
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
  await refresh()
}

async function terminate(row: ApprovalInstanceVO) {
  try {
    await ElMessageBox.confirm(`确定终止「${row.title}」吗？终止后流程实例状态置为「已终止」。`, '终止确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  await terminateInstanceApi(row.id)
  ElMessage.success('已终止')
  await refresh()
}

async function suspend(row: ApprovalInstanceVO) {
  try {
    await ElMessageBox.confirm(
      `确定挂起「${row.title}」吗？挂起后审批任务会暂时退出审批人的待办，可随时恢复。`,
      '挂起确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await suspendInstanceApi(row.id)
  ElMessage.success('已挂起')
  await refresh()
}

async function resume(row: ApprovalInstanceVO) {
  try {
    await ElMessageBox.confirm(
      `确定恢复「${row.title}」吗？恢复后审批任务将重新回到审批人的待办。`,
      '恢复确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await resumeInstanceApi(row.id)
  ElMessage.success('已恢复')
  await refresh()
}

async function remove(row: ApprovalInstanceVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」吗？删除后该流程实例将从列表中移除。`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteInstanceApi(row.id)
  ElMessage.success('已删除')
  await refresh()
}

onMounted(async () => {
  // 列表由 useListQuery 自动加载，这里只取筛选下拉的流程选项
  try {
    flowOptions.value = await getFlowOptionsApi()
  } catch {
    flowOptions.value = []
  }
})
</script>

<template>
  <div class="instance">
    <ListPage
      :data="list"
      :total="total"
      :loading="loading"
      :page-no="pageNo"
      :page-size="pageSize"
      empty-text="暂无流程实例"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    >
      <template #filters>
        <el-form :inline="true" @submit.prevent="search">
          <el-form-item label="标题">
            <el-input v-model="query.title" placeholder="模糊匹配" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item label="发起人">
            <el-input v-model="query.applicantName" placeholder="姓名模糊" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item label="流程">
            <el-select v-model="query.flowCode" placeholder="全部" clearable style="width: 160px">
              <el-option v-for="f in flowOptions" :key="f.flowCode" :label="f.flowName" :value="f.flowCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="草稿" :value="1" />
              <el-option label="审批中" :value="2" />
              <el-option label="已通过" :value="3" />
              <el-option label="已驳回" :value="4" />
              <el-option label="已撤销" :value="5" />
              <el-option label="已终止" :value="6" />
              <el-option label="已挂起" :value="7" />
            </el-select>
          </el-form-item>
          <el-form-item label="发起时间">
            <el-date-picker
              v-model="query.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
      </template>

      <el-table-column prop="title" label="申请标题" min-width="200" />
      <el-table-column prop="flowName" label="流程名称" width="140" />
      <el-table-column prop="applicantName" label="发起人" width="120" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.statusName }}</el-tag>
          <el-tag v-if="row.overdue" type="danger" size="small" class="instance__overdue">超时</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNode" label="当前节点" width="150">
        <template #default="{ row }">{{ row.currentNode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="发起时间" width="180" />
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button icon-only link type="primary" @click="showDetail(row)">详情</el-button>
          <el-button icon-only
            v-if="row.status === STATUS_IN_PROGRESS && row.applicantId === userStore.userInfo?.userId"
            v-permission="'system:approval:withdraw'"
            link
            type="danger"
            @click="withdraw(row)"
          >
            撤销
          </el-button>
          <el-button icon-only
            v-if="row.status === STATUS_IN_PROGRESS"
            v-permission="'system:approval:suspend'"
            link
            type="info"
            @click="suspend(row)"
          >
            挂起
          </el-button>
          <el-button icon-only
            v-if="row.status === STATUS_SUSPENDED"
            v-permission="'system:approval:resume'"
            link
            type="primary"
            @click="resume(row)"
          >
            恢复
          </el-button>
          <el-button icon-only
            v-if="row.status === STATUS_IN_PROGRESS || row.status === STATUS_SUSPENDED"
            v-permission="'system:approval:terminate'"
            link
            type="warning"
            @click="terminate(row)"
          >
            终止
          </el-button>
          <el-button icon-only
            v-permission="'system:approval:delete'"
            link
            type="danger"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </ListPage>

    <ApprovalDetailDrawer v-model:visible="detailVisible" :instance-id="detailId" />
  </div>
</template>

<style scoped>
.instance__overdue {
  margin-left: 4px;
}
</style>
