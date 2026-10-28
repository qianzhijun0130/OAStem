<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RECRUIT_SOURCE_OPTIONS, RECRUIT_STAGE_OPTIONS, type RecruitCandidateVO } from '@/api/recruit'
import CandidateFormDialog from '../dialogs/CandidateFormDialog.vue'
import CandidateDetailDrawer from '../dialogs/CandidateDetailDrawer.vue'
import StageDialog from '../dialogs/StageDialog.vue'
import { useRecruitCandidate } from '../composables/useRecruitCandidate'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 「候选人」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>三个入口都在本页签：新增/编辑弹窗、阶段流转弹窗、详情抽屉（抽屉自取自管）。
 * 新增与阶段流转都会改变「可安排面试 / 可发 Offer」的候选人集合，故发 `changed`。</p>
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
} = useRecruitCandidate()

const formRef = ref<InstanceType<typeof CandidateFormDialog> | null>(null)
const stageRef = ref<InstanceType<typeof StageDialog> | null>(null)
const detailRef = ref<InstanceType<typeof CandidateDetailDrawer> | null>(null)

onMounted(load)

async function ensureDialogOptions() {
  await Promise.all([
    props.options.ensurePosts(),
    props.options.ensureDemands(),
    props.options.ensureUsers(),
  ])
}

async function openCreate() {
  await ensureDialogOptions()
  formRef.value?.openCreate()
}

async function openEdit(row: RecruitCandidateVO) {
  await ensureDialogOptions()
  formRef.value?.openEdit(row)
}

function onSaved() {
  void refresh()
  emit('changed')
}

function onStageChanged() {
  void refresh()
  emit('changed')
}

defineExpose({ refresh })
</script>

<template>
  <div class="recruit__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="姓名 / 手机号" clearable style="width: 200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="阶段">
        <el-select v-model="query.stage" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="o in RECRUIT_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="query.source" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="o in RECRUIT_SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="recruit__toolbar">
      <el-button v-permission="'hr:recruit:candidate'" type="primary" data-action="create" @click="openCreate">
        新增候选人
      </el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="name" label="姓名" width="100" />
    <el-table-column prop="mobileMasked" label="手机号" width="130" />
    <el-table-column prop="genderLabel" label="性别" width="70" />
    <el-table-column prop="postName" label="应聘职位" min-width="120" show-overflow-tooltip />
    <el-table-column prop="sourceLabel" label="来源" width="100" />
    <el-table-column label="阶段" width="100">
      <template #default="{ row }">
        <el-tag effect="plain">{{ row.stageLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="interviewCount" label="面试次数" width="90" />
    <el-table-column prop="referrerName" label="内推人" width="100" />
    <el-table-column label="操作" width="170" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button icon-only link type="primary" title="详情" data-action="detail" @click="detailRef?.open(row)">
          详情
        </el-button>
        <el-button
          v-permission="'hr:recruit:candidate'"
          icon-only
          link
          type="warning"
          title="阶段流转"
          data-action="stage"
          @click="stageRef?.open(row)"
        >
          阶段
        </el-button>
        <el-button
          v-permission="'hr:recruit:candidate'"
          icon-only
          link
          type="success"
          title="编辑"
          data-action="edit"
          @click="openEdit(row)"
        >
          编辑
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

  <CandidateFormDialog ref="formRef" :options="options" @saved="onSaved" />
  <StageDialog ref="stageRef" @saved="onStageChanged" />
  <CandidateDetailDrawer ref="detailRef" />
</template>
