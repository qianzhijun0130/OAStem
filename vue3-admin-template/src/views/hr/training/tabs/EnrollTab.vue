<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TrainEnrollVO, TrainSessionVO } from '@/api/training'
import BatchEnrollDialog from '../dialogs/BatchEnrollDialog.vue'
import ScoreDialog from '../dialogs/ScoreDialog.vue'
import type { TrainEnrollBoard } from '../composables/useTrainEnroll'
import type { UserVO } from '@/api/types'

/**
 * 「报名管理」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>花名册状态在页面创建的 `enroll` 看板里，本组件只消费（与「成绩管理」共用）。
 * 换期次即由看板内部 watch 重新取数，所以这里没有 onMounted 加载。</p>
 */
const props = defineProps<{
  /** 页面唯一实例：报名与成绩两个页签共用同一份期次花名册 */
  enroll: TrainEnrollBoard
  sessionOptions: TrainSessionVO[]
  userCandidates: UserVO[]
}>()

const board = props.enroll.state
const { checkin, load } = props.enroll

const batchRef = ref<InstanceType<typeof BatchEnrollDialog> | null>(null)
const scoreRef = ref<InstanceType<typeof ScoreDialog> | null>(null)

function openBatch() {
  if (board.sessionId == null) {
    ElMessage.warning('请先选择期次')
    return
  }
  batchRef.value?.open(board.sessionId)
}

function openScore(row: TrainEnrollVO) {
  scoreRef.value?.open(row)
}
</script>

<template>
  <div class="training__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="期次">
        <el-select v-model="board.sessionId" clearable filterable placeholder="请选择期次" style="width: 280px">
          <el-option
            v-for="s in props.sessionOptions"
            :key="s.id"
            :label="`${s.courseName} · ${s.sessionName}`"
            :value="s.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="training__toolbar">
      <el-button v-permission="'hr:training:enroll'" type="primary" data-action="batch" @click="openBatch">
        批量报名
      </el-button>
    </div>
  </div>

  <el-table v-loading="board.loading" :data="board.list" border>
    <el-table-column prop="userName" label="姓名" width="100" />
    <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
    <el-table-column prop="enrollWayLabel" label="报名方式" width="100" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="checkinTime" label="签到时间" width="160" />
    <el-table-column label="成绩" width="80">
      <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
    </el-table-column>
    <el-table-column prop="assignReason" label="指派原因" min-width="140" show-overflow-tooltip />
    <el-table-column label="操作" width="180" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:training:score'"
          icon-only
          link
          type="primary"
          title="成绩录入"
          data-action="score"
          @click="openScore(row)"
        >
          成绩
        </el-button>
        <el-button icon-only link type="success" title="签到" data-action="checkin" @click="checkin(row)">签到</el-button>
      </template>
    </el-table-column>
  </el-table>

  <BatchEnrollDialog ref="batchRef" :user-candidates="userCandidates" @saved="load" />
  <ScoreDialog ref="scoreRef" @saved="load" />
</template>
