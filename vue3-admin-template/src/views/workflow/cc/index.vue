<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCcPageApi, readAllCcApi, readCcApi } from '@/api/approval'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import ListPage from '@/components/list/ListPage.vue'
import ApprovalDetailDrawer from '@/views/workflow/components/ApprovalDetailDrawer.vue'
import type { ApprovalCcVO } from '@/api/types'

/**
 * 抄送任务
 *
 * <p>只展示「被抄送给我」的单据。抄送是告知性动作：不产生待办、不影响审批流转，
 * 因此这里只提供查看详情与标记已读，没有审批操作。</p>
 */

const TAB_UNREAD = 'unread'
const TAB_ALL = 'all'

const activeTab = ref<string>(TAB_UNREAD)

const detailVisible = ref(false)
const detailInstanceId = ref<number>()

const { list, total, loading, query, pageNo, pageSize, refresh, search,
        handleSizeChange, handlePageChange } =
  useListQuery<ApprovalCcVO, { readFlag?: number }>({
    initialQuery: () => ({ readFlag: 0 }),
    // 分页参数由 useListQuery 注入，这里原样透传
    fetcher: (params) => getCcPageApi(params),
    onError: (error) => logAsyncError('加载抄送任务失败', error),
  })

/** 切换未读/全部：readFlag 为 undefined 表示不限 */
function onTabChange() {
  query.readFlag = activeTab.value === TAB_UNREAD ? 0 : undefined
  void search()
}

function openDetail(row: ApprovalCcVO) {
  detailInstanceId.value = row.instanceId
  detailVisible.value = true
}

async function markRead(row: ApprovalCcVO) {
  try {
    await readCcApi(row.id)
    ElMessage.success('已标记为已读')
    await refresh()
  } catch (error) {
    logAsyncError('标记已读失败', error)
  }
}

async function markAllRead() {
  try {
    await readAllCcApi()
    ElMessage.success('已全部标记为已读')
    await refresh()
  } catch (error) {
    logAsyncError('全部标记已读失败', error)
  }
}

function statusType(status?: number): 'success' | 'info' | 'danger' | 'warning' | 'primary' {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 6) return 'primary'
  if (status === 5 || status === 7) return 'info'
  return 'warning'
}
</script>

<template>
  <div class="cc">
    <ListPage
      :data="list"
      :total="total"
      :loading="loading"
      :page-no="pageNo"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      pagination-layout="total, sizes, prev, pager, next"
      empty-text="暂无抄送任务"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    >
      <template #filters>
        <div class="cc__toolbar">
          <el-radio-group v-model="activeTab" size="small" @change="onTabChange">
            <el-radio-button :value="TAB_UNREAD">未读</el-radio-button>
            <el-radio-button :value="TAB_ALL">全部</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="markAllRead">全部标记已读</el-button>
        </div>
      </template>

      <el-table-column prop="title" label="审批单" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="cc__title">{{ row.title }}</span>
          <el-tag v-if="row.readFlag === 0" size="small" type="danger" effect="plain" class="cc__dot">未读</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="flowName" label="流程名称" width="120" show-overflow-tooltip />
      <el-table-column prop="applicantName" label="发起人" width="88" show-overflow-tooltip />
      <el-table-column prop="companyName" label="所属公司" width="130" show-overflow-tooltip />
      <el-table-column prop="deptName" label="所属部门" width="100" show-overflow-tooltip />
      <el-table-column label="流程状态" width="92">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="statusType(row.status)">{{ row.statusName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNode" label="当前节点" width="100" show-overflow-tooltip />
      <el-table-column prop="ccByName" label="抄送人" width="90" show-overflow-tooltip />
      <el-table-column prop="remark" label="抄送说明" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="抄送时间" width="150" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button icon-only link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button icon-only v-if="row.readFlag === 0" link type="primary" size="small" @click="markRead(row)">
            标记已读
          </el-button>
        </template>
      </el-table-column>
    </ListPage>

    <ApprovalDetailDrawer v-model:visible="detailVisible" :instance-id="detailInstanceId" />
  </div>
</template>

<style scoped>
.cc__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.cc__title {
  margin-right: 6px;
}

.cc__dot {
  transform: scale(0.85);
}
</style>
