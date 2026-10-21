<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deleteDictDataApi,
  deleteDictTypeApi,
  getDictDataPageApi,
  getDictTypePageApi,
  saveDictDataApi,
  saveDictTypeApi,
  updateDictDataStatusApi,
  updateDictTypeStatusApi,
} from '@/api/dict'
import type {
  DictDataPageQuery,
  DictDataSaveRequest,
  DictDataVO,
  DictTypeSaveRequest,
  DictTypeVO,
} from '@/api/types'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import ListPage from '@/components/list/ListPage.vue'

/** el-tag 支持的样式类型，用于配置字典项的标签颜色 */
const CSS_CLASS_OPTIONS = ['info', 'primary', 'success', 'warning', 'danger']

/* ========== 左侧：字典类型 ========== */

const typeLoading = ref(false)
const typeList = ref<DictTypeVO[]>([])
const typeKeyword = ref('')
const activeType = ref<DictTypeVO | null>(null)

const typeDialogVisible = ref(false)
const typeSubmitting = ref(false)
const typeFormRef = ref<FormInstance>()
const editingTypeId = ref<number | null>(null)

const typeForm = reactive<DictTypeSaveRequest>({
  dictName: '',
  dictType: '',
  status: 1,
  remark: '',
})

const typeRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [
    { required: true, message: '请输入字典类型编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字与下划线', trigger: 'blur' },
  ],
}

const filteredTypes = computed(() => {
  const keyword = typeKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return typeList.value
  }
  return typeList.value.filter(
    (item) =>
      item.dictName.toLowerCase().includes(keyword) || item.dictType.toLowerCase().includes(keyword),
  )
})

/* ========== 右侧：字典数据项 ========== */

/**
 * 右侧字典数据的查询条件。
 *
 * <p>不含 {@code dictType}：它由左侧选中的类型决定，属于「上下文」而非用户可填条件，
 * 由 fetcher 在取数时合并，避免它出现在筛选 UI 里。</p>
 */
interface DictDataQuery extends Omit<DictDataPageQuery, 'pageNo' | 'pageSize' | 'dictType'> {}

// 右栏分页/加载态交给组合式函数（immediate=false：挂载时尚未确定字典类型）
const {
  list: dataList,
  total: dataTotal,
  loading: dataLoading,
  query: dataQuery,
  pageNo,
  pageSize,
  refresh: refreshData,
  search: searchData,
  reset: resetData,
  handleSizeChange: handleDataSizeChange,
  handlePageChange: handleDataPageChange,
} = useListQuery<DictDataVO, DictDataQuery>({
  initialQuery: () => ({ itemLabel: undefined, status: undefined }),
  fetcher: (params) => {
    const dictType = activeType.value?.dictType
    if (!dictType) {
      // 未选中类型：返回空页而不是发一个无 dictType 的请求（后者会查出全部数据）
      return Promise.resolve({ records: [], total: 0 })
    }
    return getDictDataPageApi({ ...params, dictType })
  },
  immediate: false,
  onError: (error) => logAsyncError('加载字典数据失败', error),
})

const dataDialogVisible = ref(false)
const dataSubmitting = ref(false)
const dataFormRef = ref<FormInstance>()
const editingDataId = ref<number | null>(null)

const dataForm = reactive<DictDataSaveRequest>({
  dictType: '',
  itemLabel: '',
  itemValue: '',
  sort: 0,
  status: 1,
  cssClass: '',
  isDefault: 0,
  remark: '',
})

const dataRules: FormRules = {
  itemLabel: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  itemValue: [{ required: true, message: '请输入数据值', trigger: 'blur' }],
}

async function loadTypes() {
  typeLoading.value = true
  try {
    // 字典类型量级很小（通常十几条），一次拉全量以便左侧直接本地过滤
    const page = await getDictTypePageApi({ pageNo: 1, pageSize: 100 })
    typeList.value = page.records
    if (typeList.value.length === 0) {
      activeType.value = null
      dataList.value = []
      dataTotal.value = 0
      return
    }
    const stillExists = typeList.value.find((item) => item.id === activeType.value?.id)
    selectType(stillExists ?? typeList.value[0])
  } finally {
    typeLoading.value = false
  }
}

function selectType(type: DictTypeVO) {
  activeType.value = type
  // 切换类型等价于「清空条件 + 回第 1 页 + 重新加载」
  void resetData()
}

/* ---------- 字典类型操作 ---------- */

function openTypeDialog(row?: DictTypeVO) {
  editingTypeId.value = row?.id ?? null
  Object.assign(typeForm, {
    dictName: row?.dictName ?? '',
    dictType: row?.dictType ?? '',
    status: row?.status ?? 1,
    remark: row?.remark ?? '',
  })
  typeDialogVisible.value = true
}

async function submitType() {
  const valid = await typeFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  typeSubmitting.value = true
  try {
    await saveDictTypeApi({ ...typeForm, id: editingTypeId.value })
    ElMessage.success(editingTypeId.value ? '字典类型已更新' : '字典类型已新增')
    typeDialogVisible.value = false
    await loadTypes()
  } finally {
    typeSubmitting.value = false
  }
}

async function toggleTypeStatus(row: DictTypeVO) {
  const next = row.status === 1 ? 0 : 1
  await updateDictTypeStatusApi(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await loadTypes()
}

async function removeType(row: DictTypeVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除字典类型「${row.dictName}」吗？该类型下存在数据项时无法删除。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteDictTypeApi(row.id)
  ElMessage.success('已删除')
  await loadTypes()
}

/* ---------- 字典数据项操作 ---------- */

function openDataDialog(row?: DictDataVO) {
  if (!activeType.value) {
    ElMessage.warning('请先在左侧选择字典类型')
    return
  }
  editingDataId.value = row?.id ?? null
  Object.assign(dataForm, {
    dictType: row?.dictType ?? activeType.value.dictType,
    itemLabel: row?.itemLabel ?? '',
    itemValue: row?.itemValue ?? '',
    sort: row?.sort ?? 0,
    status: row?.status ?? 1,
    cssClass: row?.cssClass ?? '',
    isDefault: row?.isDefault ?? 0,
    remark: row?.remark ?? '',
  })
  dataDialogVisible.value = true
}

async function submitData() {
  const valid = await dataFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  dataSubmitting.value = true
  try {
    await saveDictDataApi({ ...dataForm, id: editingDataId.value })
    ElMessage.success(editingDataId.value ? '字典项已更新' : '字典项已新增')
    dataDialogVisible.value = false
    await refreshData()
  } finally {
    dataSubmitting.value = false
  }
}

async function toggleDataStatus(row: DictDataVO) {
  const next = row.status === 1 ? 0 : 1
  await updateDictDataStatusApi(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await refreshData()
}

async function removeData(row: DictDataVO) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.itemLabel}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteDictDataApi(row.id)
  ElMessage.success('已删除')
  await refreshData()
}

onMounted(loadTypes)
</script>

<template>
  <el-row :gutter="16" class="dict">
    <el-col :xs="24" :md="9">
      <el-card shadow="never" class="dict__panel">
        <template #header>
          <div class="dict__header">
            <span class="dict__title">字典类型</span>
            <el-button v-permission="'system:dict:add'" type="primary" size="small" @click="openTypeDialog()">
              新增
            </el-button>
          </div>
        </template>

        <el-input v-model="typeKeyword" placeholder="搜索字典名称或编码" clearable class="dict__search" />

        <div v-loading="typeLoading" class="type-list">
          <div
            v-for="item in filteredTypes"
            :key="item.id"
            class="type-list__item"
            :class="{ 'type-list__item--active': item.id === activeType?.id }"
            @click="selectType(item)"
          >
            <div class="type-list__main">
              <div class="type-list__name">
                {{ item.dictName }}
                <el-tag v-if="item.status !== 1" size="small" type="info">已停用</el-tag>
              </div>
              <div class="type-list__code">{{ item.dictType }}</div>
            </div>
            <div class="type-list__actions" @click.stop>
              <el-button v-permission="'system:dict:edit'" link type="primary" size="small" @click="openTypeDialog(item)">
                编辑
              </el-button>
              <el-button v-permission="'system:dict:edit'" link size="small" @click="toggleTypeStatus(item)">
                {{ item.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button v-permission="'system:dict:delete'" link type="danger" size="small" @click="removeType(item)">
                删除
              </el-button>
            </div>
          </div>
          <el-empty v-if="!typeLoading && filteredTypes.length === 0" description="暂无字典类型" :image-size="60" />
        </div>
      </el-card>
    </el-col>

    <el-col :xs="24" :md="15">
      <el-card shadow="never" class="dict__panel">
        <template #header>
          <div class="dict__header">
            <span class="dict__title">
              字典数据
              <el-tag v-if="activeType" size="small" type="info">{{ activeType.dictType }}</el-tag>
            </span>
            <div class="dict__filters">
              <el-input
                v-model="dataQuery.itemLabel"
                placeholder="显示名称"
                clearable
                size="small"
                class="dict__filter-item"
                @keyup.enter="searchData"
              />
              <el-button size="small" @click="searchData">查询</el-button>
              <el-button v-permission="'system:dict:add'" type="primary" size="small" @click="openDataDialog()">
                新增
              </el-button>
            </div>
          </div>
        </template>

        <ListPage
          :card="false"
          :data="dataList"
          :total="dataTotal"
          :loading="dataLoading"
          :page-no="pageNo"
          :page-size="pageSize"
          pagination-layout="total, sizes, prev, pager, next"
          @size-change="handleDataSizeChange"
          @current-change="handleDataPageChange"
        >
          <el-table-column prop="itemLabel" label="显示名称" min-width="120" />
          <el-table-column prop="itemValue" label="数据值" min-width="100" />
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="标签样式" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.cssClass" size="small" :type="row.cssClass">{{ row.cssClass }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="默认" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault === 1" size="small" type="success">是</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch
                v-permission="'system:dict:edit'"
                :model-value="row.status === 1"
                @change="toggleDataStatus(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button icon-only v-permission="'system:dict:edit'" link type="primary" size="small" @click="openDataDialog(row)">
                编辑
              </el-button>
              <el-button icon-only v-permission="'system:dict:delete'" link type="danger" size="small" @click="removeData(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </ListPage>
      </el-card>
    </el-col>
  </el-row>

  <ResponsiveDialog
    v-model="typeDialogVisible"
    :title="editingTypeId ? '编辑字典类型' : '新增字典类型'"
    width="480px"
  >
    <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="110px">
      <el-form-item label="字典名称" prop="dictName">
        <el-input v-model="typeForm.dictName" placeholder="如 公告类型" maxlength="100" />
      </el-form-item>
      <el-form-item label="类型编码" prop="dictType">
        <el-input
          v-model="typeForm.dictType"
          :disabled="Boolean(editingTypeId)"
          placeholder="如 notice_type"
          maxlength="100"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="typeForm.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="255" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="typeDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="typeSubmitting" @click="submitType">确定</el-button>
    </template>
  </ResponsiveDialog>

  <ResponsiveDialog
    v-model="dataDialogVisible"
    :title="editingDataId ? '编辑字典数据' : '新增字典数据'"
    width="520px"
  >
    <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="110px">
      <el-form-item label="字典类型">
        <el-input :model-value="dataForm.dictType" disabled />
      </el-form-item>
      <el-form-item label="显示名称" prop="itemLabel">
        <el-input v-model="dataForm.itemLabel" placeholder="如 紧急" maxlength="100" />
      </el-form-item>
      <el-form-item label="数据值" prop="itemValue">
        <el-input v-model="dataForm.itemValue" placeholder="如 3" maxlength="100" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="dataForm.sort" :min="0" :max="9999" />
      </el-form-item>
      <el-form-item label="标签样式">
        <el-select v-model="dataForm.cssClass" placeholder="可选，用于列表标签颜色" clearable>
          <el-option v-for="item in CSS_CLASS_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="设为默认">
        <el-switch v-model="dataForm.isDefault" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="dataForm.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="dataForm.remark" type="textarea" :rows="2" maxlength="255" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dataDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="dataSubmitting" @click="submitData">确定</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped>
.dict__panel {
  height: 100%;
}

.dict__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dict__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.dict__filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dict__filter-item {
  width: 140px;
}

.dict__search {
  margin-bottom: 12px;
}

.type-list {
  max-height: 560px;
  overflow-y: auto;
}

.type-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
}

.type-list__item:hover {
  background-color: #f5f7fa;
}

.type-list__item--active {
  background-color: #ecf5ff;
}

.type-list__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
}

.type-list__code {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.type-list__actions {
  display: none;
  gap: 4px;
  white-space: nowrap;
}

.type-list__item:hover .type-list__actions,
.type-list__item--active .type-list__actions {
  display: flex;
}

</style>
