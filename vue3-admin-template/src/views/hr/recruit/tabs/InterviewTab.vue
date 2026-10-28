<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RECRUIT_INTERVIEW_STATUS_OPTIONS, type RecruitInterviewVO } from '@/api/recruit'
import InterviewFormDialog from '../dialogs/InterviewFormDialog.vue'
import FeedbackDialog from '../dialogs/FeedbackDialog.vue'
import SummarizeDialog from '../dialogs/SummarizeDialog.vue'
import { useRecruitInterview } from '../composables/useRecruitInterview'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 「面试」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>三个弹窗各自独立：安排面试（需要候选人/职位/面试官下拉）、提交评价（纯评分）、
 * 汇总结论（先拉面试详情判断是否全员已评）。三者成功后都只重载本页签列表。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

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
} = useRecruitInterview()

const formRef = ref<InstanceType<typeof InterviewFormDialog> | null>(null)
const feedbackRef = ref<InstanceType<typeof FeedbackDialog> | null>(null)
const summarizeRef = ref<InstanceType<typeof SummarizeDialog> | null>(null)

// 候选人筛选器的下拉就来自共享 options，所以挂载时要把它拉起来
onMounted(async () => {
  await props.options.ensureCandidates()
  await load()
})

async function openCreate() {
  await Promise.all([
    props.options.ensureCandidates(),
    props.options.ensurePosts(),
    props.options.ensureUsers(),
  ])
  formRef.value?.openCreate()
}

function openFeedback(row: RecruitInterviewVO) {
  feedbackRef.value?.open(row)
}

function openSummarize(row: RecruitInterviewVO) {
  void summarizeRef.value?.open(row)
}

defineExpose({ refresh })
</script>

<template>
  <div class="recruit__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="候选人">
        <el-select v-model="query.candidateId" clearable filterable placeholder="全部" style="width: 180px">
          <el-option
            v-for="c in props.options.state.candidateOptions"
            :key="c.id"
            :label="`${c.name}（${c.mobileMasked}）`"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="o in RECRUIT_INTERVIEW_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="recruit__toolbar">
      <el-button v-permission="'hr:recruit:interview'" type="primary" data-action="create" @click="openCreate">
        安排面试
      </el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="candidateName" label="候选人" width="100" />
    <el-table-column prop="postName" label="职位" min-width="120" show-overflow-tooltip />
    <el-table-column prop="roundNo" label="轮次" width="70" />
    <el-table-column prop="interviewTime" label="面试时间" width="160" />
    <el-table-column prop="wayLabel" label="方式" width="80" />
    <el-table-column label="面试官" min-width="160" show-overflow-tooltip>
      <template #default="{ row }">{{ (row.interviewerNames || []).join('、') || '-' }}</template>
    </el-table-column>
    <el-table-column label="评价" width="110">
      <template #default="{ row }">{{ row.feedbackCount }}/{{ row.interviewerCount }}</template>
    </el-table-column>
    <el-table-column prop="conclusionLabel" label="结论" width="90" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="180" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button icon-only link type="primary" title="提交评价" data-action="feedback" @click="openFeedback(row)">
          评价
        </el-button>
        <el-button
          v-permission="'hr:recruit:interview'"
          icon-only
          link
          type="success"
          title="汇总结论"
          data-action="summarize"
          :disabled="!row.allSubmitted"
          @click="openSummarize(row)"
        >
          汇总
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

  <InterviewFormDialog ref="formRef" :options="options" @saved="refresh" />
  <FeedbackDialog ref="feedbackRef" @saved="refresh" />
  <SummarizeDialog ref="summarizeRef" @saved="refresh" />
</template>
