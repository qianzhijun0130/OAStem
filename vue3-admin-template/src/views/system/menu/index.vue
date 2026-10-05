<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { deleteMenuApi, getMenuTreeApi, saveMenuApi, updateMenuStatusApi } from '@/api/menu'
import type { MenuSaveRequest, MenuVO } from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'

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

async function loadTree() {
  loading.value = true
  try {
    const all = await getMenuTreeApi()
    treeData.value = keyword.value.trim()
      ? filterByName(all, keyword.value.trim())
      : all
  } catch (error) {
    logAsyncError('加载菜单树失败', error)
  } finally {
    loading.value = false
  }
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

onMounted(loadTree)
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
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
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
            <el-button
              v-permission="'system:menu:edit'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
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

    <el-dialog
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
          <el-input v-model="form.icon" placeholder="Element Plus 图标名（可选）" />
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
    </el-dialog>
  </div>
</template>

<style scoped>
.menu__toolbar {
  margin-bottom: 16px;
}
</style>
