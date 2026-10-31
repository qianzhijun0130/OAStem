<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  exportCompletenessApi,
  getCompletenessItemsApi,
  getCompletenessSummaryApi,
} from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { CompletenessCard, CompletenessItemVO } from '@/api/types'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'

/**
 * 建档完整度看板。
 *
 * <p>为什么做成自包含组件：员工档案页已经不短（含筛选、列表、编辑抽屉、合同与证照），
 * 而这块看板与档案页没有任何状态交集——它只读、独立取数、点卡片弹自己的清单。
 * 塞进主页面会让「改档案」和「看缺口」两种改动互相干扰。</p>
 *
 * <p>为什么卡片可点：只给数字没用。HR 看到「无在职合同 30 人」之后，
 * 下一个动作一定是「哪 30 个人」。卡片本身就是入口，比让用户自己回去筛列表更直接。</p>
 */

const cards = ref<CompletenessCard[]>([])
const totalOnDuty = ref(0)
const loading = ref(false)

const dialogVisible = ref(false)
const activeCard = ref<CompletenessCard | null>(null)
const rows = ref<CompletenessItemVO[]>([])
const rowsLoading = ref(false)
const exporting = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadSummary() {
  loading.value = true
  try {
    const data = await getCompletenessSummaryApi()
    cards.value = data.cards || []
    totalOnDuty.value = data.totalOnDuty || 0
  } catch (error) {
    logAsyncError('loadCompletenessSummary', error)
  } finally {
    loading.value = false
  }
}

async function loadRows() {
  if (!activeCard.value) {
    return
  }
  rowsLoading.value = true
  try {
    const page = await getCompletenessItemsApi({
      dimension: activeCard.value.dimension,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    rows.value = page.records || []
    total.value = page.total || 0
  } catch (error) {
    logAsyncError('loadCompletenessItems', error)
  } finally {
    rowsLoading.value = false
  }
}

function openCard(card: CompletenessCard) {
  // 计数为 0 时不弹窗：弹出来一张空表只会让人以为加载失败
  if (!card.count) {
    ElMessage.info(`「${card.label}」当前没有待补人员`)
    return
  }
  activeCard.value = card
  pageNo.value = 1
  total.value = 0
  rows.value = []
  dialogVisible.value = true
  void loadRows()
}

function onPageChange(page: number) {
  pageNo.value = page
  void loadRows()
}

async function exportList() {
  if (!activeCard.value) {
    return
  }
  exporting.value = true
  try {
    await exportCompletenessApi(activeCard.value.dimension, activeCard.value.label)
  } catch (error) {
    logAsyncError('exportCompleteness', error)
  } finally {
    exporting.value = false
  }
}

onMounted(loadSummary)

defineExpose({ refresh: loadSummary })
</script>

<template>
  <div v-loading="loading" class="completeness">
    <div class="completeness__head">
      <span class="completeness__title">建档完整度</span>
      <span class="completeness__sub">在职 {{ totalOnDuty }} 人 · 点击卡片查看待补清单</span>
    </div>
    <div class="completeness__cards">
      <button
        v-for="card in cards"
        :key="card.dimension"
        type="button"
        class="completeness__card"
        :class="{ 'is-clean': !card.count }"
        :data-dimension="card.dimension"
        @click="openCard(card)"
      >
        <span class="completeness__label">{{ card.label }}</span>
        <span class="completeness__count">
          {{ card.count }}<i>人</i>
        </span>
        <span class="completeness__percent">占 {{ card.percent }}%</span>
      </button>
    </div>

    <ResponsiveDialog
      v-model="dialogVisible"
      :title="activeCard ? `建档完整度 - ${activeCard.label}` : '建档完整度'"
      width="720px"
    >
      <div class="completeness__dialog-toolbar">
        <span class="completeness__hint">
          共 {{ total }} 人待补。请在「编辑档案」或「合同管理」中补齐，本清单只读。
        </span>
        <el-button
          v-permission="'hr:employee:export'"
          :loading="exporting"
          data-action="export"
          @click="exportList"
        >
          导出
        </el-button>
      </div>
      <el-table :data="rows" size="small" border stripe max-height="420">
        <el-table-column prop="workNumber" label="工号" width="110" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
        <el-table-column prop="employmentStatusLabel" label="任职状态" width="90" />
        <el-table-column prop="entryDate" label="入职日期" width="110" />
        <el-table-column label="直属上级" width="110">
          <template #default="{ row }">{{ row.directLeaderName || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rowsLoading && !rows.length" description="没有待补人员" :image-size="60" />
      <el-pagination
        v-if="total > pageSize"
        class="completeness__pager"
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNo"
        @current-change="onPageChange"
      />
    </ResponsiveDialog>
  </div>
</template>

<style scoped>
.completeness {
  margin-bottom: 12px;
}

.completeness__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.completeness__title {
  font-size: 14px;
  font-weight: 600;
}

.completeness__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.completeness__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.completeness__card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.completeness__card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.completeness__card.is-clean {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}

.completeness__label {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.completeness__count {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-color-warning);
}

.completeness__card.is-clean .completeness__count {
  color: var(--el-color-success);
}

.completeness__count i {
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  margin-left: 2px;
}

.completeness__percent {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.completeness__dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.completeness__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.completeness__pager {
  margin-top: 8px;
  justify-content: flex-end;
}
</style>
