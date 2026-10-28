<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RECRUIT_DEMAND_STATUS_OPTIONS, type RecruitDemandVO } from '@/api/recruit'
import DemandFormDialog from '../dialogs/DemandFormDialog.vue'
import { useRecruitDemand } from '../composables/useRecruitDemand'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 「招聘需求」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>本页签是流水线起点：审批通过/关闭/新增都改变「可关联需求」集合，
 * 因此操作成功后发 `changed`，由页面刷共享的需求下拉。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ changed: [] }>()

const {
  list,
  loading,
  query,
  total,
  pageNo,
  pageSize,
  load,
  search,
  reset,
  refresh,
  handleSizeChange,
  handlePageChange,
  approve,
  close,
} = useRecruitDemand()

const formRef = ref<InstanceType<typeof DemandFormDialog> | null>(null)

// 部门树只服务本页签的筛选器，所以由本页签触发（首次取过即命中缓存）
onMounted(async () => {
  await props.options.ensureDeptTree()
  await load()
})

/** 弹窗要用岗位与员工下拉，打开前补一次 */
async function openCreate() {
  await Promise.all([props.options.ensureUsers(), props.options.ensurePositions()])
  formRef.value?.openCreate()
}

async function openEdit(row: RecruitDemandVO) {
  await Promise.all([props.options.ensureUsers(), props.options.ensurePositions()])
  formRef.value?.openEdit(row)
}

function onSaved() {
  void refresh()
  emit('changed')
}

async function onApprove(row: RecruitDemandVO) {
  if (await approve(row)) {
    emit('changed')
  }
}

async function onClose(row: RecruitDemandVO) {
  if (await close(row)) {
    emit('changed')
  }
}

defineExpose({ refresh })
</script>

<template>
  <div class="recruit__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="需求编码 / 原因" clearable style="width: 200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="部门">
        <el-tree-select
          v-model="query.deptId"
          :data="props.options.state.deptTree"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          placeholder="全部部门"
          style="width: 160px"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="o in RECRUIT_DEMAND_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="recruit__toolbar">
      <el-button v-permission="'hr:recruit:demand'" type="primary" data-action="create" @click="openCreate">
        新增需求
      </el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="demandCode" label="需求编码" width="150" />
    <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
    <el-table-column prop="positionName" label="岗位" min-width="120" show-overflow-tooltip />
    <el-table-column label="人数" width="80" prop="headCount" />
    <el-table-column label="紧急度" width="90">
      <template #default="{ row }">
        <el-tag :type="row.urgency === 2 ? 'danger' : 'info'" effect="plain">{{ row.urgencyLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="expectDate" label="期望到岗" width="120" />
    <el-table-column prop="hrName" label="负责HR" width="100" />
    <el-table-column prop="hiredCount" label="已招" width="70" />
    <el-table-column label="缺口" width="70">
      <template #default="{ row }">
        <span :class="{ 'recruit__over': row.overQuota }">{{ row.quotaGap ?? '-' }}</span>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="210" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:recruit:demand'"
          icon-only
          link
          type="success"
          title="审批通过"
          data-action="approve"
          :disabled="row.status !== 1"
          @click="onApprove(row)"
        >
          审批
        </el-button>
        <el-button
          v-permission="'hr:recruit:demand'"
          icon-only
          link
          type="warning"
          title="编辑"
          data-action="edit"
          @click="openEdit(row)"
        >
          编辑
        </el-button>
        <el-button
          v-permission="'hr:recruit:demand'"
          icon-only
          link
          type="danger"
          title="关闭"
          data-action="close"
          :disabled="row.status === 5"
          @click="onClose(row)"
        >
          关闭
        </el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    class="recruit__pagination"
    :current-page="pageNo"
    :page-size="pageSize"
    :page-sizes="[10, 20, 50]"
    :total="total"
    layout="total, sizes, prev, pager, next, jumper"
    background
    @size-change="handleSizeChange"
    @current-change="handlePageChange"
  />

  <DemandFormDialog ref="formRef" :options="options" @saved="onSaved" />
</template>
