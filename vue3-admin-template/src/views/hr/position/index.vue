<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deletePositionApi,
  getPositionPageApi,
  savePositionApi,
  updatePositionStatusApi,
} from '@/api/hr'
import { getDeptTreeApi } from '@/api/dept'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import type { DeptVO, PositionSaveRequest, PositionVO } from '@/api/types'

const deptTree = ref<DeptVO[]>([])

const { list, total, loading, query, pageNo, pageSize, load, search, reset, refresh, handleSizeChange, handlePageChange } =
  useListQuery<PositionVO, { keyword: string; deptId?: number; series?: string; status?: number }>({
    initialQuery: () => ({ keyword: '', deptId: undefined, series: undefined, status: undefined }),
    fetcher: (params) => getPositionPageApi(params),
  })

const SERIES_OPTIONS = [
  { value: 'M', label: '管理序列' },
  { value: 'P', label: '技术序列' },
  { value: 'S', label: '职能序列' },
  { value: 'O', label: '操作序列' },
]

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<PositionSaveRequest>({
  positionCode: '',
  positionName: '',
  deptId: undefined,
  series: 'P',
  levelCode: '',
  headcount: 0,
  description: '',
  status: 1,
  sort: 0,
})

const rules: FormRules = {
  positionCode: [
    { required: true, message: '请输入岗位编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '只能包含字母、数字、下划线与中划线', trigger: 'blur' },
  ],
  positionName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
}

/** 编制列文案：未设置编制时只显示在岗人数 */
function headcountText(row: PositionVO): string {
  if (!row.headcount) {
    return `在岗 ${row.onDutyCount} 人`
  }
  return `${row.onDutyCount} / ${row.headcount}`
}

const dialogTitle = computed(() => (editingId.value ? '编辑岗位' : '新增岗位'))

function resetForm() {
  Object.assign(form, {
    positionCode: '',
    positionName: '',
    deptId: undefined,
    series: 'P',
    levelCode: '',
    headcount: 0,
    description: '',
    status: 1,
    sort: 0,
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: PositionVO) {
  editingId.value = row.id
  Object.assign(form, {
    positionCode: row.positionCode,
    positionName: row.positionName,
    deptId: row.deptId,
    series: row.series || 'P',
    levelCode: row.levelCode || '',
    headcount: row.headcount || 0,
    description: row.description || '',
    status: row.status,
    sort: row.sort || 0,
  })
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await savePositionApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    dialogVisible.value = false
    await refresh()
  } catch (error) {
    // 拦截器已提示，这里只记日志
    logAsyncError('savePosition', error)
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: PositionVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updatePositionStatusApi(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    await refresh()
  } catch (error) {
    logAsyncError('togglePositionStatus', error)
  }
}

async function remove(row: PositionVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除岗位「${row.positionName}」吗？该操作不可撤销。`,
      '删除岗位',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deletePositionApi(row.id)
    ElMessage.success('已删除')
    await refresh()
  } catch (error) {
    logAsyncError('deletePosition', error)
  }
}

onMounted(async () => {
  try {
    deptTree.value = (await getDeptTreeApi()) || []
  } catch (error) {
    logAsyncError('loadDeptTree', error)
  }
  await load()
})
</script>

<template>
  <div class="hr-position">
    <el-card shadow="never">
      <div class="hr-position__head">
        <el-form :inline="true" class="hr-position__filters" @submit.prevent>
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              placeholder="岗位编码 / 名称"
              clearable
              style="width: 200px"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="部门">
            <el-tree-select
              v-model="query.deptId"
              :data="deptTree"
              :props="{ label: 'deptName', value: 'id', children: 'children' }"
              node-key="id"
              check-strictly
              clearable
              placeholder="全部部门"
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="序列">
            <el-select v-model="query.series" clearable placeholder="全部" style="width: 140px">
              <el-option v-for="s in SERIES_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="hr-position__toolbar">
          <el-button v-permission="'hr:position:create'" type="primary" @click="openCreate">新增岗位</el-button>
        </div>
      </div>

      <el-alert
        class="hr-position__tip"
        type="info"
        :closable="false"
        show-icon
        title="编制只做超编提示，不拦截入职与调岗；「在岗」口径为试用 / 正式 / 待离职，不含待入职与已离职。"
      />

      <el-table v-loading="loading" :data="list" border row-key="id">
        <el-table-column prop="positionCode" label="岗位编码" width="140" />
        <el-table-column prop="positionName" label="岗位名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="deptName" label="所属部门" min-width="140" show-overflow-tooltip />
        <el-table-column label="序列" width="110">
          <template #default="{ row }">
            {{ SERIES_OPTIONS.find((s) => s.value === row.series)?.label || row.series }}
          </template>
        </el-table-column>
        <el-table-column prop="levelCode" label="职级" width="90">
          <template #default="{ row }">{{ row.levelCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="在岗 / 编制" width="130">
          <template #default="{ row }">
            <span :class="{ 'hr-position__over': row.overHeadcount }">{{ headcountText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="岗位说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'hr:position:update'" icon-only link type="primary" title="编辑" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-permission="'hr:position:update'"
              icon-only
              link
              type="warning"
              :title="row.status === 1 ? '停用' : '启用'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="'hr:position:delete'" icon-only link type="danger" title="删除" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="hr-position__pagination"
        :current-page="pageNo"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="岗位编码" prop="positionCode">
          <el-input v-model="form.positionCode" maxlength="50" placeholder="如 DEV_ENGINEER" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="positionName">
          <el-input v-model="form.positionName" maxlength="100" placeholder="如 Java 工程师" />
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="请选择部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="岗位序列">
          <el-select v-model="form.series" style="width: 100%">
            <el-option v-for="s in SERIES_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="职级">
          <el-input v-model="form.levelCode" maxlength="16" placeholder="如 P5 / M2，可不填" />
        </el-form-item>
        <el-form-item label="编制数">
          <el-input-number v-model="form.headcount" :min="0" :max="9999" controls-position="right" />
          <span class="hr-position__hint">0 表示未设置编制</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="岗位说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hr-position__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.hr-position__filters {
  flex: 1 1 auto;
}

.hr-position__toolbar {
  flex: 0 0 auto;
}

.hr-position__tip {
  margin: 4px 0 12px;
}

.hr-position__over {
  color: var(--el-color-danger);
  font-weight: 600;
}

.hr-position__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.hr-position__hint {
  margin-left: 8px;
  color: #8a94a5;
  font-size: 12px;
}
</style>
