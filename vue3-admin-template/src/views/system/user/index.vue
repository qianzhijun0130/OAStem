<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deleteUserApi,
  downloadUserImportTemplateApi,
  getUserPageApi,
  importUsersApi,
  resetUserPwdApi,
  saveUserApi,
  updateUserStatusApi,
} from '@/api/user'
import { getRoleOptionsApi } from '@/api/role'
import { getDeptTreeApi } from '@/api/dept'
import type {
  DeptVO,
  RoleOptionVO,
  UserImportResultVO,
  UserPageQuery,
  UserSaveRequest,
  UserVO,
} from '@/api/types'
import { useListQuery } from '@/composables/useListQuery'
import ListPage from '@/components/list/ListPage.vue'

/** 与后端 UserServiceImpl.DEFAULT_PASSWORD 保持一致：新增 / 重置密码共用同一初始密码 */
const DEFAULT_PASSWORD = 'qwe@123456'
const STATUS_OPTIONS = [
  { value: 0, label: '禁用' },
  { value: 1, label: '启用' },
  { value: 2, label: '锁定' },
]
/** 业务上已取消「未知」：员工性别在入职时必然已知，默认男 */
const GENDER_OPTIONS = [
  { value: 1, label: '男' },
  { value: 2, label: '女' },
]
const DEFAULT_GENDER = 1
/** 导入失败明细最多展示条数，超出部分只提示总数 */
const MAX_ERROR_LIST = 20

const deptTree = ref<DeptVO[]>([])
const roleOptions = ref<RoleOptionVO[]>([])

/** 页面私有查询条件：分页参数由 useListQuery 统一管理 */
interface UserQuery extends Omit<UserPageQuery, 'pageNo' | 'pageSize'> {}

const { list, total, loading, query, pageNo, pageSize, refresh, search, reset,
        handleSizeChange, handlePageChange } =
  useListQuery<UserVO, UserQuery>({
    initialQuery: () => ({
      deptId: undefined,
      roleId: undefined,
      status: undefined,
      keyword: undefined,
    }),
    fetcher: (params) => getUserPageApi(params),
  })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const isEdit = computed(() => editingId.value !== null)

const form = reactive<UserSaveRequest>({
  username: '',
  realName: '',
  nickname: '',
  gender: DEFAULT_GENDER,
  mobile: '',
  email: '',
  deptId: undefined,
  position: '',
  workNumber: '',
  entryDate: '',
  roleIds: [],
  password: '',
  status: 1,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{3,30}$/,
      message: '3-30 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  roleIds: [
    { required: true, type: 'array', min: 1, message: '请至少分配一个角色', trigger: 'change' },
  ],
  password: [
    {
      trigger: 'blur',
      validator: (_rule, value: string, cb) => {
        if (isEdit.value && !value) {
          cb()
          return
        }
        if (!value || /^[A-Za-z0-9_@#$%]{6,20}$/.test(value)) {
          cb()
        } else {
          cb(new Error('6-20 位字母、数字或 @#$%_ 等字符'))
        }
      },
    },
  ],
}

/**
 * 部门下拉数据直接用后端返回的部门树（**全量**，供列表页「部门」筛选使用——
 * 用户可能挂在已停用的部门下，筛选时必须能选到）。
 *
 * <p>此前这里额外套了一个 id=0 的「未分配部门」合成根节点：既让 el-tree-select
 * 默认只显示这一层、看不到真实部门，又是个死选项——后端 resolveCompanyId
 * 对 id=0 会直接抛「部门不存在」。员工必须归属真实部门，故移除该合成节点。</p>
 */

/** 编辑对象的当前部门；若该部门已停用，需临时并入表单下拉，否则保存时会把部门清空 */
const retiredDept = ref<DeptVO | null>(null)

function findDeptById(nodes: DeptVO[], id?: number | null): DeptVO | null {
  if (id === undefined || id === null) {
    return null
  }
  for (const node of nodes) {
    if (node.id === id) {
      return node
    }
    const hit = findDeptById(node.children ?? [], id)
    if (hit) {
      return hit
    }
  }
  return null
}

/**
 * 表单用部门选项：只保留启用部门（停用的父部门连同其子树一并隐藏，与菜单树语义一致）。
 * 编辑时若当前部门已停用，则把它临时置于顶层，保证「原样保存」不会被清空。
 * 后端 UserServiceImpl#resolveCompanyId 会做同样的校验（部门变更时必须是启用部门）。
 */
const formDeptOptions = computed<DeptVO[]>(() => {
  const keepEnabled = (nodes: DeptVO[]): DeptVO[] =>
    nodes
      .map((node) => ({ ...node, children: keepEnabled(node.children ?? []) }))
      .filter((node) => node.status === 1)
  const enabled = keepEnabled(deptTree.value)
  return retiredDept.value ? [retiredDept.value, ...enabled] : enabled
})

/** 今天（YYYY-MM-DD）：新增员工的入职日期默认取表单打开当日，后端亦有兜底 */
function today(): string {
  const now = new Date()
  const month = `${now.getMonth() + 1}`.padStart(2, '0')
  const day = `${now.getDate()}`.padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function openCreate() {
  editingId.value = null
  // 新增不存在「保留已停用部门」的场景
  retiredDept.value = null
  Object.assign(form, {
    id: undefined,
    username: '',
    realName: '',
    nickname: '',
    gender: DEFAULT_GENDER,
    mobile: '',
    email: '',
    deptId: undefined,
    position: '',
    workNumber: '',
    // 补录历史员工时可手工改；绝大多数场景就是当天入职
    entryDate: today(),
    // 新建员工默认「普通员工」：不预选的话很容易漏勾，造出能登录但看不到任何菜单的账号；
    // 更高角色由管理员在同一个下拉里追加（后端对空角色也做了同样兜底）。
    roleIds: defaultRoleIds(),
    password: '',
    status: 1,
  })
  dialogVisible.value = true
}

/**
 * 默认角色 id 列表：优先按角色编码匹配 EMPLOYEE，找不到再退回名称匹配。
 *
 * <p>按编码匹配是因为名称可被管理员改（如改成「基层员工」），编码是稳定标识。</p>
 */
function defaultRoleIds(): number[] {
  const hit =
    roleOptions.value.find((r) => r.roleCode === 'EMPLOYEE') ??
    roleOptions.value.find((r) => r.roleName === '普通员工')
  return hit ? [hit.id] : []
}

function openEdit(row: UserVO) {
  editingId.value = row.id
  // 当前部门若已停用（不在启用树中），临时并入表单下拉，否则保存时会把部门清空
  retiredDept.value =
    row.deptId && !findDeptById(deptTree.value, row.deptId)
      ? {
          id: row.deptId,
          parentId: 0,
          deptName: `${row.deptName ?? '未知部门'}（已停用）`,
          deptCode: '',
          companyId: row.companyId ?? 0,
          deptType: 1,
          sort: 0,
          status: 0,
          children: [],
        }
      : null
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    nickname: row.nickname ?? '',
    // 存量数据中可能存在历史「未知(0)」，编辑时按男展示，保存后即被纠正
    gender: row.gender === 2 ? 2 : DEFAULT_GENDER,
    mobile: row.mobile ?? '',
    email: row.email ?? '',
    deptId: row.deptId,
    position: row.position ?? '',
    workNumber: row.workNumber ?? '',
    entryDate: row.entryDate ?? '',
    roleIds: row.roleIds ?? [],
    password: '',
    status: row.status,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const payload: UserSaveRequest = {
      ...form,
      username: form.username.trim(),
      realName: form.realName.trim(),
    }
    if (isEdit.value && !payload.password) {
      delete payload.password
    }
    await saveUserApi(payload)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await refresh()
  } finally {
    submitting.value = false
  }
}

async function remove(row: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除员工「${row.realName}（${row.username}）」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteUserApi(row.id)
  ElMessage.success('删除成功')
  await refresh()
}

async function toggleStatus(row: UserVO, enabled: boolean) {
  await updateUserStatusApi(row.id, enabled ? 1 : 0)
  ElMessage.success(enabled ? '已启用' : '已禁用')
  await refresh()
}

async function resetPwd(row: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.realName}」的密码重置为初始密码 ${DEFAULT_PASSWORD} 吗？`,
      '重置密码',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await resetUserPwdApi(row.id)
  ElMessage.success(`密码已重置为 ${DEFAULT_PASSWORD}`)
}

// ---------------- 批量导入 ----------------

const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<UserImportResultVO | null>(null)

/** 只接受 Excel：其它格式直接拦在前端，省掉一次无效往返 */
const ACCEPTED_EXCEL = '.xlsx,.xls'

function openImport() {
  importFile.value = null
  importResult.value = null
  importVisible.value = true
}

function onImportFileChange(file: { raw?: File; name?: string }) {
  importResult.value = null
  const raw = file?.raw ?? null
  // auto-upload=false 时 before-upload 不会执行，类型校验必须落在 onChange：
  // 否则拖入 .exe 改名文件会一路发到后端（后端仍会拒，但没必要多一次往返）。
  const name = (raw?.name ?? file?.name ?? '').toLowerCase()
  if (raw && !name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    ElMessage.error('只支持 .xlsx / .xls 格式的 Excel 文件')
    importFile.value = null
    return
  }
  importFile.value = raw
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的 Excel 文件')
    return
  }
  importing.value = true
  try {
    const result = await importUsersApi(importFile.value)
    if (result.imported) {
      ElMessage.success(result.message || '导入成功')
      importVisible.value = false
      await refresh()
      return
    }
    // 整批未通过：文件本身没错，把逐行原因留在对话框里供管理员对照修改
    importResult.value = result
    ElMessage.warning(result.message || '校验未通过，本次未导入任何数据')
  } finally {
    importing.value = false
  }
}

/** 失败明细可能上千条，只展示前若干条并提示总数，避免对话框被撑爆 */
const shownImportErrors = computed(() =>
  (importResult.value?.errors ?? []).slice(0, MAX_ERROR_LIST),
)

onMounted(async () => {
  // 列表由 useListQuery 自动加载，这里只补部门树与角色下拉
  await loadDeptAndRoles()
})

async function loadDeptAndRoles() {
  const [depts, roles] = await Promise.all([getDeptTreeApi(), getRoleOptionsApi()])
  deptTree.value = depts
  roleOptions.value = roles
}
</script>

<template>
  <div class="user">
    <ListPage
      :data="list"
      :total="total"
      :loading="loading"
      :page-no="pageNo"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      pagination-layout="total, sizes, prev, pager, next, jumper"
      empty-text="暂无员工数据"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    >
      <template #filters>
        <el-form :inline="true" @submit.prevent="search">
          <el-form-item label="部门">
            <el-tree-select
              v-model="query.deptId"
              :data="deptTree"
              :props="{ label: 'deptName', children: 'children' }"
              node-key="id"
              check-strictly
              :render-after-expand="false"
              placeholder="全部部门"
              clearable
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="query.roleId" placeholder="全部" clearable style="width: 150px">
              <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="query.keyword" placeholder="姓名/账号/工号/手机" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 工具栏保持在筛选区下方独立一行（与改造前视觉一致） -->
        <div class="user__toolbar">
          <el-button v-permission="'system:user:add'" type="primary" @click="openCreate">
            新增员工
          </el-button>
          <el-button v-permission="'system:user:import'" @click="openImport">批量导入</el-button>
        </div>
      </template>

      <!-- 列宽合计超出容器时 el-table 会自动出现底部横向滚动条 -->
      <el-table-column prop="username" label="登录账号" width="120" />
      <el-table-column prop="workNumber" label="员工编号" width="120" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column label="性别" width="70">
        <template #default="{ row }">{{ row.gender === 2 ? '女' : '男' }}</template>
      </el-table-column>
      <el-table-column prop="deptName" label="所属部门" min-width="130" show-overflow-tooltip />
      <el-table-column prop="position" label="职务" width="120" show-overflow-tooltip />
      <el-table-column prop="mobile" label="手机号" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色范围" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.roleNames || '-' }}</template>
      </el-table-column>
      <!-- 状态与操作固定在右侧：横向滚动时不至于被滚出视野 -->
      <el-table-column label="人员状态" width="100" fixed="right">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="(val: boolean) => toggleStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button icon-only v-permission="'system:user:edit'" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button icon-only v-permission="'system:user:resetPwd'" link type="warning" @click="resetPwd(row)">
            重置密码
          </el-button>
          <el-button icon-only v-permission="'system:user:delete'" link type="danger" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </ListPage>

    <ResponsiveDialog
      v-model="dialogVisible"
      :title="editingId ? '编辑员工' : '新增员工'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="登录账号" prop="username">
              <el-input v-model="form.username" :disabled="isEdit" placeholder="3-30 位字母数字下划线" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="请输入真实姓名" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" placeholder="可选" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="form.gender">
                <el-radio v-for="o in GENDER_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="form.mobile" placeholder="请输入 11 位手机号" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="可选" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="formDeptOptions"
                :props="{ label: 'deptName', children: 'children' }"
                node-key="id"
                check-strictly
                :render-after-expand="false"
                placeholder="请选择部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="form.position" placeholder="可选" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="工号">
              <el-input v-model="form.workNumber" placeholder="留空按部门自动生成" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker
                v-model="form.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="默认提交当日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="分配角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <div v-if="!isEdit" class="user-form__tip">
            默认已选「普通员工」，需要更多权限请在此追加角色（保存后由管理员维护）
          </div>
        </el-form-item>
        <el-form-item label="登录密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : `默认 ${DEFAULT_PASSWORD}`"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </ResponsiveDialog>

    <ResponsiveDialog v-model="importVisible" title="批量导入员工" width="680px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="请先下载模板，按模板填写后再上传"
        description="任意一行校验失败则整批不导入，系统会给出行号与原因。新增员工的初始密码统一为系统默认初始密码。"
        style="margin-bottom: 16px"
      />
      <el-form label-width="0">
        <el-form-item>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :accept="ACCEPTED_EXCEL"
            :on-change="onImportFileChange"
          >
            <template #trigger>
              <el-button type="primary">选择 Excel 文件</el-button>
            </template>
            <template #tip>
              <div class="user__upload-tip">
                支持 .xlsx / .xls，单次不超过 5000 行
                <span v-if="importFile" class="user__upload-file">已选择：{{ importFile.name }}</span>
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <div v-if="importResult && !importResult.imported" class="user__import-errors">
        <el-divider content-position="left">校验结果（共 {{ importResult.failCount }} 行有问题）</el-divider>
        <el-table :data="shownImportErrors" border size="small" max-height="260">
          <el-table-column prop="row" label="行号" width="90" />
          <el-table-column prop="message" label="原因" min-width="200" />
        </el-table>
        <div v-if="importResult.failCount > shownImportErrors.length" class="user__import-more">
          仅显示前 {{ shownImportErrors.length }} 条，请按行号修正后重新上传
        </div>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button @click="downloadUserImportTemplateApi()">下载模板</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </template>
    </ResponsiveDialog>
  </div>
</template>

<style scoped>
.user__toolbar {
  margin-bottom: 16px;
}
/* 表单项下方的说明文字：与 el-form 的错误提示区分开（灰、不占校验位） */
.user-form__tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.user__upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
/* 已选文件名：与提示文字同色但更深，便于确认「这次传的是哪个文件」 */
.user__upload-file {
  margin-left: 8px;
  color: var(--el-text-color-regular);
}
.user__import-more {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
