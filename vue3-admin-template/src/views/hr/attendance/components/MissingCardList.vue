<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getMissingCardPageApi, getMyMissingCardsApi } from '@/api/hr'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import type { DeptVO, MissingCardVO } from '@/api/types'
import CardSupplementDialog from './CardSupplementDialog.vue'

/**
 * 漏卡待处理清单。
 *
 * <p>同一个组件服务两种视角，因为「看什么」完全一样、「能做什么」不同：</p>
 * <ul>
 *   <li>{@code mode='hr'}——分页 + 部门筛选，只读。HR 用它看谁漏卡、催人补；</li>
 *   <li>{@code mode='self'}——只查自己、不分页，带「申请补卡」。</li>
 * </ul>
 *
 * <p>口径由后端保证：只列 {@code card_missing > 0} 且该方向还没补卡的记录。
 * 所以「清单是空的」本身就是结论——没有需要处理的漏卡。</p>
 */
const props = withDefaults(defineProps<{ mode?: 'hr' | 'self'; deptTree?: DeptVO[] }>(), {
  mode: 'hr',
  deptTree: () => [],
})

const emit = defineEmits<{ changed: [] }>()

function currentMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const dialogRef = ref<InstanceType<typeof CardSupplementDialog> | null>(null)

/* ---------------- HR 视角：分页 ---------------- */

const hrQuery = useListQuery<MissingCardVO, { month: string; deptId?: number; keyword?: string }>({
  initialQuery: () => ({ month: currentMonth(), deptId: undefined, keyword: '' }),
  fetcher: (params) => getMissingCardPageApi(params),
  // 自助视角不该发这条请求：它查的是全公司，会 403
  immediate: props.mode === 'hr',
})

/* ---------------- 自助视角：全量 ---------------- */

const myMonth = ref(currentMonth())
const myList = ref<MissingCardVO[]>([])
const myLoading = ref(false)

async function loadMine() {
  myLoading.value = true
  try {
    myList.value = (await getMyMissingCardsApi(myMonth.value)) || []
  } catch (error) {
    logAsyncError('loadMyMissingCards', error)
  } finally {
    myLoading.value = false
  }
}

onMounted(() => {
  if (props.mode === 'self') {
    void loadMine()
  }
})

function onSaved() {
  if (props.mode === 'self') {
    void loadMine()
  } else {
    void hrQuery.refresh()
  }
  emit('changed')
}

function supplement(row: MissingCardVO, direction: number) {
  // 单方向缺卡时直接沿用该方向；两侧都缺时由用户在弹出的单选里选
  dialogRef.value?.open({ ...row, missingDirections: [direction] })
}

defineExpose({ refresh: props.mode === 'self' ? loadMine : () => hrQuery.refresh() })
</script>

<template>
  <div class="missing">
    <!-- HR 视角：筛选 + 分页 -->
    <template v-if="mode === 'hr'">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="月份">
          <el-date-picker v-model="hrQuery.query.month" type="month" value-format="YYYY-MM" style="width: 140px" />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="hrQuery.query.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="全部（含下级）"
            style="width: 170px"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="hrQuery.query.keyword" placeholder="姓名 / 工号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="hrQuery.search">查询</el-button>
          <el-button @click="hrQuery.reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon class="missing__tip">
        漏卡是系统已识别的事实（有排班、只有一侧刷卡），员工在「自助服务 → 我的考勤」认领即可，
        不计入每月 3 次的主动补卡额度。
      </el-alert>

      <el-table v-loading="hrQuery.loading.value" :data="hrQuery.list.value" border>
        <el-table-column prop="attendanceDate" label="日期" width="115" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="workNumber" label="工号" width="100" />
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
        <el-table-column prop="missingLabel" label="缺卡情况" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ row.attendanceStatusLabel || '-' }}</template>
        </el-table-column>
        <el-table-column label="补卡进度" min-width="140">
          <template #default="{ row }">
            <el-tag v-if="row.pendingDirections?.length" type="warning" effect="plain">已提交待审批</el-tag>
            <span v-else class="missing__none">未处理</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="missing__pager"
        :current-page="hrQuery.pageNo.value"
        :page-size="hrQuery.pageSize.value"
        :total="hrQuery.total.value"
        layout="total, prev, pager, next"
        @current-change="hrQuery.handlePageChange"
      />
    </template>

    <!-- 自助视角：只查自己 -->
    <template v-else>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="月份">
          <el-date-picker v-model="myMonth" type="month" value-format="YYYY-MM" style="width: 140px" @change="loadMine" />
        </el-form-item>
        <el-form-item>
          <span class="missing__count">
            {{ myList.length ? `本月有 ${myList.length} 天存在漏卡` : '本月没有需要处理的漏卡' }}
          </span>
        </el-form-item>
      </el-form>

      <el-table v-loading="myLoading" :data="myList" border>
        <el-table-column prop="attendanceDate" label="日期" width="120" />
        <el-table-column prop="missingLabel" label="缺卡情况" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ row.attendanceStatusLabel || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <template v-for="d in row.missingDirections" :key="d">
              <el-button
                v-if="!row.pendingDirections?.includes(d)"
                link
                type="primary"
                :data-direction="d"
                @click="supplement(row, d)"
              >
                补{{ d === 1 ? '上班' : '下班' }}卡
              </el-button>
              <el-tag v-else type="warning" effect="plain" class="missing__tag">
                {{ d === 1 ? '上班' : '下班' }}卡已提交
              </el-tag>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <p class="missing__hint">
        漏卡认领不占每月 3 次的主动补卡额度；提交后走补卡审批，通过后当天考勤自动重算。
      </p>
    </template>

    <CardSupplementDialog ref="dialogRef" @saved="onSaved" />
  </div>
</template>

<style scoped>
.missing__tip {
  margin-bottom: 12px;
}

.missing__pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.missing__count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.missing__none {
  color: var(--el-text-color-secondary);
}

.missing__tag {
  margin-right: 6px;
}

.missing__hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
