<script setup lang="ts">
import { ref } from 'vue'
import CycleFormDialog from '../dialogs/CycleFormDialog.vue'
import type { PerfCycleBoard } from '../composables/usePerfCycleBoard'
import type { PerfOptions } from '../composables/usePerfOptions'
import type { PerfCycleVO } from '@/api/types'

/**
 * 「考核周期」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>周期状态由页面共享（考核表、等级分布都在读），所以本页签不持有列表，
 * 只做展示与动作编排：接口成功后由看板自己重载，然后向页面发 `changed`，
 * 让「考核表」「等级分布」跟着刷新（推进阶段会改变每张表的可操作状态）。</p>
 */
const props = defineProps<{
  cycleBoard: PerfCycleBoard
  options: PerfOptions
}>()

const emit = defineEmits<{ changed: [] }>()

const board = props.cycleBoard
const state = board.state

const formRef = ref<InstanceType<typeof CycleFormDialog> | null>(null)

/** 新建周期要先有方案下拉，这里在打开前把选项拉好（首次取过就命中缓存） */
async function openCreate() {
  await props.options.loadPlanOptions()
  formRef.value?.openCreate()
}

async function onAdvance(row: PerfCycleVO) {
  if (await board.advance(row)) {
    emit('changed')
  }
}

async function onArchive(row: PerfCycleVO) {
  if (await board.archive(row)) {
    emit('changed')
  }
}

async function onGenerate(row: PerfCycleVO) {
  if (await board.generate(row)) {
    emit('changed')
  }
}

async function onRemove(row: PerfCycleVO) {
  if (await board.remove(row)) {
    emit('changed')
  }
}

async function onSaved() {
  await board.load()
  emit('changed')
}
</script>

<template>
  <div class="perf__toolbar">
    <el-button v-permission="'hr:performance:plan'" type="primary" plain data-action="create" @click="openCreate">
      新建周期
    </el-button>
    <span class="perf__hint">周期创建时会按方案范围自动生成考核表（入职不满 1 个月不生成）</span>
  </div>
  <el-table :data="state.list" border class="perf__cycle-table">
    <el-table-column prop="cycleName" label="周期" min-width="150" />
    <el-table-column prop="cycleCode" label="编码" width="110" />
    <el-table-column prop="planName" label="方案" min-width="130" show-overflow-tooltip />
    <el-table-column label="起止" width="190">
      <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
    </el-table-column>
    <el-table-column label="阶段" width="100">
      <template #default="{ row }">
        <el-tag :type="row.status === 2 ? 'info' : 'primary'" effect="plain">{{ row.stageLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="进度（目标/自评/评分/确认）" width="220">
      <template #default="{ row }">
        {{ row.targetDone }} / {{ row.selfDone }} / {{ row.managerDone }} / {{ row.confirmDone }}
        <span class="perf__muted">（共 {{ row.sheetCount }}）</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="290" fixed="right">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:performance:plan'"
          link
          type="primary"
          data-action="advance"
          :disabled="row.status === 2"
          @click="onAdvance(row)"
        >
          推进
        </el-button>
        <el-button
          v-permission="'hr:performance:calibrate'"
          link
          type="primary"
          data-action="archive"
          :disabled="row.status === 2"
          @click="onArchive(row)"
        >
          归档
        </el-button>
        <el-button v-permission="'hr:performance:plan'" link data-action="generate" @click="onGenerate(row)">
          补齐考核表
        </el-button>
        <el-button v-permission="'hr:performance:plan'" link type="danger" data-action="delete" @click="onRemove(row)">
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <CycleFormDialog ref="formRef" :plan-options="props.options.state.planOptions" @saved="onSaved" />
</template>
