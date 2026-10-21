<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { deleteDeptApi, getDeptTreeApi, saveDeptApi, updateDeptStatusApi } from '@/api/dept'
import type { DeptSaveRequest, DeptVO } from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'

/** 后端尚未提供用户下拉接口，负责人暂不支持在表单中编辑，仅在列表中展示 */
const DEFAULT_COMPANY_ID = 1

const loading = ref(false)
const treeData = ref<DeptVO[]>([])

const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<DeptSaveRequest>({
  deptName: '',
  deptCode: '',
  workNoPrefix: '',
  parentId: 0,
  sort: 0,
  status: 1,
  companyId: DEFAULT_COMPANY_ID,
})

const rules: FormRules = {
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 100, message: '长度不能超过 100 个字符', trigger: 'blur' },
  ],
  deptCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]+$/,
      message: '只能包含字母、数字、下划线与中划线',
      trigger: 'blur',
    },
  ],
  parentId: [{ required: true, message: '请选择上级部门', trigger: 'change' }],
  workNoPrefix: [
    {
      pattern: /^[A-Za-z0-9]{1,10}$/,
      message: '1-10 位字母或数字，如 JSB',
      trigger: 'blur',
    },
  ],
}

/** 上级部门下拉：新增时展示完整树，编辑时排除自身及其后代（避免形成环） */
const parentOptions = computed<DeptVO[]>(() => [
  {
    id: 0,
    parentId: 0,
    deptName: '顶级部门',
    deptCode: '',
    companyId: DEFAULT_COMPANY_ID,
    deptType: 1,
    sort: 0,
    status: 1,
    children: excludeSubtree(treeData.value, editingId.value),
  },
])

function excludeSubtree(nodes: DeptVO[], excludeId: number | null): DeptVO[] {
  if (excludeId === null) {
    return nodes
  }
  return nodes
    .filter((node) => node.id !== excludeId)
    .map((node) => ({ ...node, children: excludeSubtree(node.children ?? [], excludeId) }))
}

async function loadTree() {
  loading.value = true
  try {
    treeData.value = await getDeptTreeApi({
      deptName: keyword.value.trim() || undefined,
      status: statusFilter.value,
    })
  } catch (error) {
    logAsyncError('加载部门树失败', error)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  keyword.value = ''
  statusFilter.value = undefined
  void loadTree()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    id: undefined,
    deptName: '',
    deptCode: '',
    workNoPrefix: '',
    parentId: 0,
    sort: 0,
    status: 1,
    companyId: DEFAULT_COMPANY_ID,
  })
  // 复位上一次「保存成功后刻意保留」的守卫，保证新开弹窗可正常提交
  submitting.value = false
  dialogVisible.value = true
}

function openEdit(row: DeptVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    deptName: row.deptName,
    deptCode: row.deptCode,
    workNoPrefix: row.workNoPrefix ?? '',
    parentId: row.parentId,
    sort: row.sort,
    status: row.status,
    companyId: row.companyId,
  })
  // 复位上一次「保存成功后刻意保留」的守卫，保证新开弹窗可正常提交
  submitting.value = false
  dialogVisible.value = true
}

async function submit() {
  // 防重复提交：validate() 是异步的（先交给浏览器、查完再回来），
  // 若 submitting 晚于它置位，连点会落在「校验已发起、守卫尚未生效」的空档里
  // 并重入本函数，产生多次保存请求。故置位必须提到最前，并在入口拦截进行中的调用。
  if (submitting.value) {
    return
  }
  if (!formRef.value) {
    return
  }
  let saved = false
  submitting.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) {
      return
    }
    await saveDeptApi({
      ...form,
      deptName: form.deptName.trim(),
      deptCode: form.deptCode.trim(),
      // 留空由后端按部门名称自动推导；填写时统一大写，避免同一前缀因大小写被判成两个部门
      workNoPrefix: form.workNoPrefix?.trim() ? form.workNoPrefix.trim().toUpperCase() : undefined,
    })
    // 保存成功：弹窗进入关闭动画期间按钮仍在 DOM 中，此处刻意不复位守卫，
    // 否则会重新打开提交窗口（请求约 90ms 就返回，关闭动画还要约 300ms）。
    // 守卫改由 openCreate / openEdit 在下次打开时统一复位。
    saved = true
    ElMessage.success(editingId.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await loadTree()
  } catch (error) {
    // 具体错误信息已由请求拦截器统一提示；此处兜底避免未处理的 Promise rejection
    console.error('保存部门失败', error)
  } finally {
    // 仅「校验未通过 / 保存失败」复位，保证弹窗仍可重试
    if (!saved) {
      submitting.value = false
    }
  }
}

async function remove(row: DeptVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除部门「${row.deptName}」吗？存在下级部门或部门下有用户时将无法删除。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteDeptApi(row.id)
  ElMessage.success('删除成功')
  await loadTree()
}

async function toggleStatus(row: DeptVO, enabled: boolean) {
  try {
    await updateDeptStatusApi(row.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '已启用' : '已禁用')
    await loadTree()
  } catch (error) {
    logAsyncError('切换部门状态失败', error)
  }
}

onMounted(loadTree)
</script>

<template>
  <div class="dept">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="loadTree">
        <el-form-item label="部门名称">
          <el-input v-model="keyword" placeholder="请输入部门名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTree">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="dept__toolbar">
        <el-button v-permission="'system:dept:add'" type="primary" @click="openCreate">
          新增部门
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        border
      >
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column prop="deptCode" label="部门编码" width="140" />
        <el-table-column prop="workNoPrefix" label="工号前缀" width="110">
          <template #default="{ row }">{{ row.workNoPrefix || '-' }}</template>
        </el-table-column>
        <el-table-column prop="leaderName" label="负责人" width="120">
          <template #default="{ row }">
            {{ row.leaderName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(val: boolean) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button icon-only
              v-permission="'system:dept:edit'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button icon-only
              v-permission="'system:dept:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无部门数据" />
        </template>
      </el-table>
    </el-card>

    <ResponsiveDialog
      v-model="dialogVisible"
      :title="editingId ? '编辑部门' : '新增部门'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="请输入部门名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model="form.deptCode" placeholder="如 TECH、HR" maxlength="50" />
        </el-form-item>
        <el-form-item label="工号前缀" prop="workNoPrefix">
          <el-input
            v-model="form.workNoPrefix"
            placeholder="留空自动按部门名生成，如 技术部 → JSB"
            maxlength="16"
          />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </ResponsiveDialog>
  </div>
</template>

<style scoped>
.dept__toolbar {
  margin-bottom: 16px;
}
</style>
