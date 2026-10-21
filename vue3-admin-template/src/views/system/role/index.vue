<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  assignRoleMenusApi,
  cloneRoleMenusApi,
  deleteRoleApi,
  getRoleMenusApi,
  getRoleOptionsApi,
  getRolePageApi,
  saveRoleApi,
  updateRoleStatusApi,
} from '@/api/role'
import { getMenuTreeApi } from '@/api/menu'
import type { MenuVO, RoleOptionVO, RolePageQuery, RoleSaveRequest, RoleVO } from '@/api/types'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import ListPage from '@/components/list/ListPage.vue'

const DATA_SCOPE_OPTIONS = [
  { value: 1, label: '全部数据' },
  { value: 2, label: '本部门' },
  { value: 3, label: '仅本人' },
]

/** 页面私有查询条件：分页参数由 useListQuery 统一管理 */
interface RoleQuery extends Omit<RolePageQuery, 'pageNo' | 'pageSize'> {}

// 查询/分页/加载态交给组合式函数，页面只保留业务动作
const { list, total, loading, query, pageNo, pageSize, refresh, search, reset,
        handleSizeChange, handlePageChange } =
  useListQuery<RoleVO, RoleQuery>({
    initialQuery: () => ({ roleName: undefined, status: undefined }),
    fetcher: (params) => getRolePageApi(params),
    onError: (error) => logAsyncError('加载角色列表失败', error),
  })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<RoleSaveRequest>({
  roleName: '',
  roleCode: '',
  roleType: 2,
  dataScope: 3,
  description: '',
  sort: 0,
  status: 1,
})

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]+$/,
      message: '只能包含字母、数字与下划线',
      trigger: 'blur',
    },
  ],
  roleType: [{ required: true, message: '请选择角色类型', trigger: 'change' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
}

/** 权限分配对话框 */
const permDialogVisible = ref(false)
const permRoleId = ref<number | null>(null)
const permRoleName = ref('')
const permTreeLoading = ref(false)
const permSaving = ref(false)
const menuTree = ref<MenuVO[]>([])
const checkedMenuIds = ref<number[]>([])
const permTreeRef = ref()

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    id: undefined,
    roleName: '',
    roleCode: '',
    roleType: 2,
    dataScope: 3,
    description: '',
    sort: 0,
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: RoleVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    roleType: row.roleType ?? 2,
    dataScope: row.dataScope ?? 3,
    description: row.description ?? '',
    sort: row.sort ?? 0,
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
    await saveRoleApi({
      ...form,
      roleName: form.roleName.trim(),
      roleCode: form.roleCode.trim(),
    })
    ElMessage.success(editingId.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await refresh()
  } finally {
    submitting.value = false
  }
}

async function remove(row: RoleVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.roleName}」吗？角色已分配给用户时无法删除。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteRoleApi(row.id)
  ElMessage.success('删除成功')
  await refresh()
}

async function toggleStatus(row: RoleVO, enabled: boolean) {
  await updateRoleStatusApi(row.id, enabled ? 1 : 0)
  ElMessage.success(enabled ? '已启用' : '已禁用')
  await refresh()
}

/** 把菜单树摊平成 id 列表，供「全选」使用 */
function flattenMenuIds(list: MenuVO[]): number[] {
  const ids: number[] = []
  const walk = (nodes: MenuVO[]) => {
    for (const node of nodes) {
      ids.push(node.id)
      if (node.children?.length) {
        walk(node.children)
      }
    }
  }
  walk(list)
  return ids
}

const allMenuIds = computed<number[]>(() => flattenMenuIds(menuTree.value))

async function openPermDialog(row: RoleVO) {
  permRoleId.value = row.id
  permRoleName.value = row.roleName
  permDialogVisible.value = true
  permTreeLoading.value = true
  checkedMenuIds.value = []
  try {
    const [tree, ids] = await Promise.all([getMenuTreeApi(), getRoleMenusApi(row.id)])
    menuTree.value = tree
    checkedMenuIds.value = ids
    // 必须等树渲染完成再回显，否则拿到的是空节点树
    await nextTick()
    // 用 setCheckedKeys 而不是 default-checked-keys：后者内部只做「补勾选」
    // （node.setChecked(true)），不会清掉上一个角色残留的已勾选项，
    // 连续给多个角色分配权限时会互相污染。
    permTreeRef.value?.setCheckedKeys(ids ?? [], false)
  } finally {
    permTreeLoading.value = false
  }
}

/**
 * 保存权限。
 *
 * <p>只提交实际勾选的节点，刻意<b>不含半选（indeterminate）父节点</b>：
 * 半选父节点一旦入库，回显时会被视作「已勾选」并向下级联成全选，
 * 再保存一次就把整棵树的权限都写进去了——这正是「分配权限总是变成全选」的根因。</p>
 *
 * <p>上级目录的补全交给后端（RoleServiceImpl#expandAncestors）：侧边栏按菜单树渲染，
 * 父目录缺失会让子菜单变成挂不上树的孤儿节点。</p>
 */
async function savePerms() {
  if (permRoleId.value === null) {
    return
  }
  const checked: number[] = permTreeRef.value?.getCheckedKeys?.(false) ?? []
  permSaving.value = true
  try {
    await assignRoleMenusApi(permRoleId.value, checked)
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
    await refresh()
  } finally {
    permSaving.value = false
  }
}

function checkAllMenus() {
  permTreeRef.value?.setCheckedKeys(allMenuIds.value, false)
}

function clearAllMenus() {
  permTreeRef.value?.setCheckedKeys([], false)
}

/* ---------------- 克隆菜单 ---------------- */

const cloneDialogVisible = ref(false)
const cloneSaving = ref(false)
const cloneTargetId = ref<number | null>(null)
const cloneTargetName = ref('')
const cloneSourceId = ref<number | undefined>(undefined)
const cloneSourceOptions = ref<RoleOptionVO[]>([])

/**
 * 打开「克隆菜单」对话框。
 *
 * <p>系统没有角色继承/权限叠加机制，菜单必须逐角色独立完整勾选。给新角色授权时若从零勾选，
 * 极易漏掉基础菜单（工作台、我的公告等）——权限接口返回成功，但侧边栏缺节点、页面点不开。
 * 以成熟角色为基线克隆可规避该问题。</p>
 */
async function openCloneDialog(row: RoleVO) {
  cloneTargetId.value = row.id
  cloneTargetName.value = row.roleName
  cloneSourceId.value = undefined
  cloneDialogVisible.value = true
  try {
    const opts = await getRoleOptionsApi()
    // 排除自身与超级管理员：前者无意义，后者由系统保证全量、接口会直接拒绝
    cloneSourceOptions.value = (opts ?? []).filter(
      (o) => o.id !== row.id && o.roleCode !== 'SUPER_ADMIN',
    )
  } catch (error) {
    logAsyncError('加载可克隆角色列表失败', error)
  }
}

async function submitClone() {
  if (cloneTargetId.value === null) {
    return
  }
  if (cloneSourceId.value === undefined) {
    ElMessage.warning('请选择作为基线的源角色')
    return
  }
  const sourceName = cloneSourceOptions.value.find((o) => o.id === cloneSourceId.value)?.roleName ?? ''
  try {
    await ElMessageBox.confirm(
      `将把「${sourceName}」当前已授权的全部菜单复制到「${cloneTargetName.value}」。` +
        `该操作会**覆盖**「${cloneTargetName.value}」现有的全部权限，且不可撤销。是否继续？`,
      '克隆菜单',
      { type: 'warning', confirmButtonText: '确认克隆', cancelButtonText: '取消' },
    )
  } catch {
    // 用户点了取消，ElMessageBox 抛 cancel，保持弹窗打开以便改选源角色
    return
  }
  cloneSaving.value = true
  try {
    await cloneRoleMenusApi(cloneTargetId.value, cloneSourceId.value)
    ElMessage.success('菜单已克隆，请按需增删')
    cloneDialogVisible.value = false
    await refresh()
  } catch (error) {
    logAsyncError('克隆角色菜单失败', error)
  } finally {
    cloneSaving.value = false
  }
}

/** 展开 / 折叠全部：逐个节点设置 expanded，el-tree 没有直接的 toggleAll 方法 */
function toggleExpandAll(expanded: boolean) {
  const nodesMap = permTreeRef.value?.store?.nodesMap ?? {}
  Object.values(nodesMap).forEach((node) => {
    ;(node as { expanded?: boolean }).expanded = expanded
  })
}

function scopeLabel(scope?: number) {
  return DATA_SCOPE_OPTIONS.find((o) => o.value === scope)?.label ?? '-'
}
</script>

<template>
  <div class="role">
    <ListPage
      :data="list"
      :total="total"
      :loading="loading"
      :page-no="pageNo"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      pagination-layout="total, sizes, prev, pager, next, jumper"
      empty-text="暂无角色数据"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    >
      <template #filters>
        <el-form :inline="true" @submit.prevent="search">
          <el-form-item label="角色名称">
            <el-input v-model="query.roleName" placeholder="请输入角色名称" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 工具栏保持在筛选区下方的独立一行（与改造前视觉一致） -->
        <div class="role__toolbar">
          <el-button v-permission="'system:role:add'" type="primary" @click="openCreate">
            新增角色
          </el-button>
        </div>
      </template>

      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="roleCode" label="角色编码" min-width="140" />
      <el-table-column label="角色类型" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.roleType === 1" type="danger">系统角色</el-tag>
          <el-tag v-else type="info">业务角色</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据范围（暂未生效）" width="170">
        <template #default="{ row }">{{ scopeLabel(row.dataScope) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="userCount" label="用户数" width="90" align="center" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="(val: boolean) => toggleStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button icon-only v-permission="'system:role:edit'" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button icon-only v-permission="'system:role:assign'" link type="success" @click="openPermDialog(row)">
            分配权限
          </el-button>
          <el-button icon-only
            v-permission="'system:role:assign'"
            link
            type="warning"
            @click="openCloneDialog(row)"
          >
            克隆菜单
          </el-button>
          <el-button icon-only v-permission="'system:role:delete'" link type="danger" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </ListPage>

    <ResponsiveDialog
      v-model="dialogVisible"
      :title="editingId ? '编辑角色' : '新增角色'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 HR_MANAGER" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色类型" prop="roleType">
          <el-radio-group v-model="form.roleType">
            <el-radio :value="1">系统角色</el-radio>
            <el-radio :value="2">业务角色</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="o in DATA_SCOPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <!--
            诚实标注：该字段当前仅保存入库，尚未参与任何查询的数据过滤。
            在实现数据权限之前，明确告知使用者「配了也还不起作用」，
            避免误以为已经生效。详见 开发文档/操作日志 2026-10-02-01。
          -->
          <div class="role__tip">该配置当前仅保存，暂未参与数据过滤（后续版本生效）</div>
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
        <el-form-item label="状态">
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

    <ResponsiveDialog
      v-model="permDialogVisible"
      :title="`分配权限 - ${permRoleName}`"
      width="560px"
      :close-on-click-modal="false"
    >
      <div class="role__perm-toolbar">
        <el-checkbox
          :model-value="false"
          @change="(val: boolean) => toggleExpandAll(val)"
        >
          展开/折叠
        </el-checkbox>
        <el-button link type="primary" @click="checkAllMenus">全选</el-button>
        <el-button link type="primary" @click="clearAllMenus">清空</el-button>
        <span class="role__perm-tip">父子不联动，请按需逐项勾选；上级目录由系统自动补全</span>
      </div>
      <div v-loading="permTreeLoading" class="role__perm-tree">
        <el-tree
          ref="permTreeRef"
          :data="menuTree"
          :props="{ label: 'menuName', children: 'children' }"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
          :render-after-expand="false"
        />
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerms">保存</el-button>
      </template>
    </ResponsiveDialog>

    <ResponsiveDialog v-model="cloneDialogVisible" :title="`克隆菜单 - ${cloneTargetName}`" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        <template #title>
          系统没有「角色继承 / 权限叠加」机制，每个角色的菜单都必须独立完整勾选。
          以成熟角色为基线克隆，可避免漏勾基础菜单（工作台、我的公告等）导致
          「权限已分配但侧边栏缺节点、页面点不开」。
        </template>
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="目标角色">
          <el-input :model-value="cloneTargetName" disabled />
        </el-form-item>
        <el-form-item label="基线角色">
          <el-select
            v-model="cloneSourceId"
            placeholder="请选择作为基线的源角色"
            style="width: 100%"
          >
            <el-option
              v-for="opt in cloneSourceOptions"
              :key="opt.id"
              :label="`${opt.roleName}（${opt.roleCode}）`"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <span class="role__perm-tip">
            将复制该角色<b>当前已授权的全部菜单</b>，并<b>覆盖</b>目标角色现有权限。复制后可再按需增删。
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cloneDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="cloneSaving" @click="submitClone">确认克隆</el-button>
      </template>
    </ResponsiveDialog>
  </div>
</template>

<style scoped>
.role__toolbar {
  margin-bottom: 16px;
}
.role__tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.role__perm-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.role__perm-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.role__perm-tree {
  max-height: 420px;
  overflow: auto;
}
</style>
