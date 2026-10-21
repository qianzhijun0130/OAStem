<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ChevronsDownUp, ChevronsUpDown } from '@lucide/vue'
import AppIcon from '@/components/icons/AppIcon.vue'
import { LUCIDE_ICON_NAMES } from '@/utils/icons'
import { deleteMenuApi, getMenuTreeApi, saveMenuApi, updateMenuStatusApi } from '@/api/menu'
import type { MenuSaveRequest, MenuVO } from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'

/** el-table 实例类型：仅声明本项目实际调用的方法 */
type MenuTableInstance = {
  getExpandedRowKeys: () => Array<number | string>
  setAllRowsExpansion: (expand: boolean) => void
  toggleRowExpansion: (row: unknown, expanded?: boolean) => void
}

const tableRef = ref<MenuTableInstance | null>(null)

const MENU_TYPE_OPTIONS = [
  { value: 1, label: '目录' },
  { value: 2, label: '菜单' },
  { value: 3, label: '按钮' },
]

const loading = ref(false)
const treeData = ref<MenuVO[]>([])

const keyword = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive<MenuSaveRequest>({
  menuName: '',
  menuCode: '',
  menuType: 1,
  path: '',
  component: '',
  icon: '',
  parentId: 0,
  sort: 0,
  visible: 1,
  status: 1,
})

const rules = computed<FormRules>(() => ({
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuCode: [
    { required: true, message: '请输入权限标识', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_:]+$/,
      message: '只能包含字母、数字、冒号与下划线',
      trigger: 'blur',
    },
  ],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
  // 条件必填：与后端 MenuServiceImpl#assertTypeFieldsValid 保持同一口径，
  // 避免再出现「前端不显示该字段、后端却无条件必填」的前后端契约打架。
  path: form.menuType !== 3 ? [{ required: true, message: '请输入路由地址', trigger: 'blur' }] : [],
  component:
    form.menuType === 2 ? [{ required: true, message: '请输入组件路径', trigger: 'blur' }] : [],
}))

/** 上级菜单下拉：仅目录(1)与菜单(2)可作为父级，编辑时排除自身及其后代 */
const parentOptions = computed<MenuVO[]>(() => [
  {
    id: 0,
    parentId: 0,
    menuName: '顶级目录',
    menuCode: '',
    menuType: 1,
    sort: 0,
    visible: 1,
    status: 1,
    children: excludeSubtree(treeData.value, editingId.value),
  },
])

function excludeSubtree(nodes: MenuVO[], excludeId: number | null): MenuVO[] {
  return nodes
    .filter((node) => node.menuType !== 3)
    .filter((node) => (excludeId === null ? true : node.id !== excludeId))
    .map((node) => ({ ...node, children: excludeSubtree(node.children ?? [], excludeId) }))
}

/** 是否处于「全部展开」状态：所有可展开节点都在展开集合里 */
const allExpanded = computed(() => {
  const total = collectExpandableIds(treeData.value).length
  return total > 0 && expandedRowKeys.value.length === total
})

async function loadTree() {
  loading.value = true
  try {
    const all = await getMenuTreeApi()
    treeData.value = keyword.value.trim()
      ? filterByName(all, keyword.value.trim())
      : all
    // 数据源变化后 el-table 的展开态会重置，这里显式回填用户当前的展开范围，
    // 使「保存 → 刷新树」后仍停留在原来的展开位置。
    // 必须等 DOM 更新完成，否则 tableRef 与行对象都还是旧的。
    await nextTick()
    restoreExpandedRows()
  } catch (error) {
    logAsyncError('加载菜单树失败', error)
  } finally {
    loading.value = false
  }
}

/**
 * 递归收集树中所有非叶子节点的 id —— 即「全部展开」时需要展开的行。
 *
 * <p>菜单树里只有目录（menuType=1）有子节点，但判定依据用「是否有 children」而非
 * menuType，这样即便后续数据异常多出空 children 也不会漏。</p>
 */
function collectExpandableIds(nodes: MenuVO[]): number[] {
  const ids: number[] = []
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      ids.push(node.id)
      ids.push(...collectExpandableIds(node.children))
    }
  }
  return ids
}

/**
 * 当前展开的行 key 集合。
 *
 * <p><b>为什么必须自己记</b>：el-table 的 {@code default-expand-all} 只在**首次渲染**时生效，
 * {@code toggleRowExpansion} 又需要在数据加载后调用。若只依赖 {@code default-expand-all}，
 * 保存后重拉数据（{@code loadTree} 整体替换 {@code treeData}）就会让展开态全部收起 ——
 * 这正是「保存一下树就折叠了」的原因。</p>
 *
 * <p>类型用 {@code number[]}：后端主键是数字，el-table 的 {@code getExpandedRowKeys()}
 * 返回值在部分版本会带字符串，这里统一 {@code Number()} 转换，避免类型不一致导致
 * {@code :expand-row-keys} 绑定失效。</p>
 */
const expandedRowKeys = ref<number[]>([])

/** 程序化改展开态时置位，避免 expand-change 把中间态写回 expandedRowKeys */
let suppressExpandEvent = false

/** 记录当前已展开的行，供数据刷新后回填。
 *
 * <p>只在<b>用户主动点击</b>展开/收起时更新状态：{@code setAllRowsExpansion} 与
 * {@code toggleRowExpansion} 是程序化调用，同样会触发 expand-change，
 * 若不加标志位会把「正在回填的中间态」写回状态，导致状态抖动。</p>
 */
function rememberExpandedRows() {
  if (suppressExpandEvent) {
    return
  }
  // el-table 部分版本返回 string 键，统一转数字以匹配 expandedRowKeys 的类型
  expandedRowKeys.value = (tableRef.value?.getExpandedRowKeys() ?? []).map(Number)
}

/**
 * 回填展开态。
 *
 * <p>el-table 的展开/收起 API 都以**行对象**为参数，而数据在刷新后是新数组，
 * 旧行对象失效。这里遍历当前数据重新取行对象，再按 key 恢复展开状态。</p>
 */
function restoreExpandedRows() {
  const table = tableRef.value
  if (!table) {
    return
  }
  suppressExpandEvent = true
  if (expandedRowKeys.value.length === 0) {
    table.setAllRowsExpansion(false)
  } else {
    const want = new Set(expandedRowKeys.value)
    const apply = (nodes: MenuVO[]) => {
      for (const node of nodes) {
        if (!node.children || node.children.length === 0) {
          continue
        }
        table.toggleRowExpansion(node, want.has(node.id))
        apply(node.children)
      }
    }
    apply(treeData.value)
  }
  // 放开时同步真实展开态：可能已过滤掉失效 key
  expandedRowKeys.value = table.getExpandedRowKeys().map(Number)
  suppressExpandEvent = false
}

/**
 * 一键展开 / 收起。
 *
 * <p>展开时把所有非叶子行设为展开并记入 {@link expandedRowKeys}，
 * 这样后续保存刷新也能维持「全展开」；收起同理。</p>
 */
function toggleExpandAll(expand: boolean) {
  const table = tableRef.value
  if (!table) {
    return
  }
  suppressExpandEvent = true
  // 逐行设置而非 setAllRowsExpansion：
  // 后者对本表不生效（实测点击后展开图标仍为 0），且只处理当前已渲染的行。
  // 逐行遍历 + toggleRowExpansion 与 restoreExpandedRows 用同一套机制，行为一致可验证。
  const apply = (nodes: MenuVO[]) => {
    for (const node of nodes) {
      if (!node.children || node.children.length === 0) {
        continue
      }
      table.toggleRowExpansion(node, expand)
      apply(node.children)
    }
  }
  apply(treeData.value)
  suppressExpandEvent = false
  expandedRowKeys.value = expand ? collectExpandableIds(treeData.value) : []
  // expand-row-keys 是响应式绑定，但 el-table 内部在数据未变时可能不重算展开态。
  // 等一次渲染后再按 key 同步一次，确保点击即刻生效。
  nextTick(() => {
    restoreExpandedRows()
  })
  // 不弹 Toast：展开/收起是纯视图操作且结果立即可见（按钮文案与箭头同步变化），
  // 提示反而打断操作节奏。
}

function filterByName(nodes: MenuVO[], kw: string): MenuVO[] {
  const result: MenuVO[] = []
  for (const node of nodes) {
    const children = filterByName(node.children ?? [], kw)
    if (node.menuName.includes(kw) || children.length > 0) {
      result.push({ ...node, children })
    }
  }
  return result
}

function resetQuery() {
  keyword.value = ''
  void loadTree()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    id: undefined,
    menuName: '',
    menuCode: '',
    menuType: 1,
    path: '',
    component: '',
    icon: '',
    parentId: 0,
    sort: 0,
    visible: 1,
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: MenuVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    menuName: row.menuName,
    menuCode: row.menuCode,
    menuType: row.menuType,
    path: row.path ?? '',
    component: row.component ?? '',
    icon: row.icon ?? '',
    parentId: row.parentId,
    sort: row.sort,
    visible: row.visible,
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
    await saveMenuApi({
      ...form,
      menuName: form.menuName.trim(),
      menuCode: form.menuCode.trim(),
    })
    ElMessage.success(editingId.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await loadTree()
  } catch (error) {
    // 具体错误信息已由请求拦截器统一提示；此处兜底避免未处理的 Promise rejection
    console.error('保存菜单失败', error)
  } finally {
    submitting.value = false
  }
}

async function remove(row: MenuVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除菜单「${row.menuName}」吗？存在下级菜单时无法删除。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    // 用户取消确认框
    return
  }
  try {
    await deleteMenuApi(row.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch (error) {
    // 如「存在下级菜单不能删除」等业务错误，拦截器已提示，这里兜底避免未捕获异常
    console.error('删除菜单失败', error)
  }
}

async function toggleStatus(row: MenuVO, enabled: boolean) {
  try {
    await updateMenuStatusApi(row.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '已启用' : '已禁用')
    await loadTree()
  } catch (error) {
    console.error('更新菜单状态失败', error)
  }
}

async function toggleVisible(row: MenuVO, visible: boolean) {
  try {
    await saveMenuApi({
      id: row.id,
      menuName: row.menuName,
      menuCode: row.menuCode,
      menuType: row.menuType,
      path: row.path,
      component: row.component,
      icon: row.icon,
      parentId: row.parentId,
      sort: row.sort,
      visible: visible ? 1 : 0,
      status: row.status,
    })
    ElMessage.success(visible ? '已显示' : '已隐藏')
    await loadTree()
  } catch (error) {
    console.error('更新菜单显示状态失败', error)
  }
}

onMounted(async () => {
  // 首次进入默认「全折叠」，把展开控制权交给用户：
  // 改造前用的 default-expand-all 只在首次渲染生效、且与 expand-row-keys 冲突，
  // 数据一刷新就复位，反而让「保存后树就折叠了」难以复现与排查。
  // 现在展开态完全由 expandedRowKeys 驱动，行为可预期。
  await loadTree()
})
</script>

<template>
  <div class="menu">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="loadTree">
        <el-form-item label="菜单名称">
          <el-input v-model="keyword" placeholder="请输入菜单名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTree">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="menu__toolbar">
        <el-button v-permission="'system:menu:add'" type="primary" @click="openCreate">
          新增菜单
        </el-button>
        <el-button :icon="allExpanded ? ChevronsUpDown : ChevronsDownUp" @click="toggleExpandAll(!allExpanded)">
          {{ allExpanded ? '收起全部' : '展开全部' }}
        </el-button>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :expand-row-keys="expandedRowKeys"
        :tree-props="{ children: 'children' }"
        @expand-change="rememberExpandedRows"
        border
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.menuType === 1" type="warning">目录</el-tag>
            <el-tag v-else-if="row.menuType === 2" type="success">菜单</el-tag>
            <el-tag v-else type="info">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="menuCode" label="权限标识" min-width="160" />
        <el-table-column prop="path" label="路由地址" min-width="140" />
        <el-table-column prop="component" label="组件路径" min-width="160" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="显示" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.visible === 1"
              @change="(val: boolean) => toggleVisible(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(val: boolean) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button icon-only
              v-permission="'system:menu:edit'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button icon-only
              v-permission="'system:menu:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无菜单数据" />
        </template>
      </el-table>
    </el-card>

    <ResponsiveDialog
      v-model="dialogVisible"
      :title="editingId ? '编辑菜单' : '新增菜单'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio-button v-for="o in MENU_TYPE_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <!-- maxlength 与后端 MenuSaveRequest.menuName 的 @Size(max = 50) 保持一致 -->
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="路由地址" prop="path">
          <el-input v-model="form.path" placeholder="如 /system/dept" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="如 system/dept/index" />
        </el-form-item>
        <el-form-item label="权限标识" prop="menuCode">
          <el-input v-model="form.menuCode" placeholder="如 system:dept 或 system:dept:add" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="图标" prop="icon">
          <!-- 从注册表选择 Lucide 图标；allow-create 兼容直接粘贴自定义图标名 -->
          <el-select
            v-model="form.icon"
            filterable
            clearable
            allow-create
            default-first-option
            placeholder="选择 Lucide 图标（可选）"
            style="width: 100%"
          >
            <el-option v-for="name in LUCIDE_ICON_NAMES" :key="name" :label="name" :value="name">
              <span class="menu-icon-option">
                <AppIcon :name="name" :size="14" />
                {{ name }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 3" label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
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
  </div>
</template>

<style scoped>
.menu__toolbar {
  margin-bottom: 16px;
}

/* 图标下拉项：图标与名称垂直居中 */
.menu-icon-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
