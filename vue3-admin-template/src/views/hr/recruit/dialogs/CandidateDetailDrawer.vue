<script setup lang="ts">
import { ref } from 'vue'
import { getCandidateDetailApi, getInterviewPageApi } from '@/api/recruit'
import type { RecruitCandidateVO, RecruitInterviewVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 候选人详情抽屉（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：`open(row)` 时并发取「候选人详情 + 该候选人全部面试记录」，
 * 抽屉自己管加载态，不占页签状态。</p>
 */
const visible = ref(false)
const loading = ref(false)
const detail = ref<RecruitCandidateVO | null>(null)
const interviews = ref<RecruitInterviewVO[]>([])

async function open(row: RecruitCandidateVO) {
  visible.value = true
  loading.value = true
  detail.value = null
  interviews.value = []
  try {
    const [d, list] = await Promise.all([
      getCandidateDetailApi(row.id),
      getInterviewPageApi({ candidateId: row.id, pageSize: 50 }),
    ])
    detail.value = d
    interviews.value = list?.records ?? []
  } catch (error) {
    logAsyncError('candidateDetail', error)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-drawer v-model="visible" title="候选人详情" size="520px">
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="手机号(脱敏)">{{ detail.mobileMasked || detail.mobile }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detail.genderLabel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应聘职位">{{ detail.postName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.sourceLabel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ detail.stageLabel }}</el-descriptions-item>
          <el-descriptions-item label="面试次数">{{ detail.interviewCount }}</el-descriptions-item>
          <el-descriptions-item label="学历">{{ detail.education || '-' }}</el-descriptions-item>
          <el-descriptions-item label="经验">{{ detail.experience || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">面试记录</el-divider>
        <el-table :data="interviews" border size="small" class="recruit__interviews">
          <el-table-column prop="roundNo" label="轮次" width="70" />
          <el-table-column prop="interviewTime" label="时间" width="150" />
          <el-table-column prop="wayLabel" label="方式" width="70" />
          <el-table-column prop="conclusionLabel" label="结论" width="80" />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>
