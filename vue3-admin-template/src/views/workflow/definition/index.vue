<script setup lang="ts">
/**
 * 流程模板管理：列表 + bpmn-js 可视化设计器 + 节点审批人配置 + 动态表单字段配置。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import BpmnModeler from '@/components/bpmn/BpmnModeler.vue'
import {
  deleteFlowApi,
  getFlowDetailApi,
  getFlowBpmnApi,
  getFlowPageApi,
  importFlowApi,
  saveFlowApi,
  updateFlowStatusApi,
} from '@/api/flow'
import { getRoleOptionsApi } from '@/api/role'
import { getDictDataByTypeApi } from '@/api/dict'
import { getUserPageApi } from '@/api/user'
import type {
  DictDataVO,
  FlowDefinitionPageQuery,
  FlowDefinitionSaveRequest,
  FlowDefinitionVO,
  FormFieldItem,
  NodeConfigItem,
  RoleOptionVO,
  UserVO,
} from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'

/** 审批人规则类型：0未配置 1指定人员 2指定角色 3部门主管 */
const ASSIGNEE_UNSET = 0
const ASSIGNEE_USER = 1
const ASSIGNEE_ROLE = 2
const ASSIGNEE_LEADER = 3

/** BPMN 2.0 标准命名空间（前端预解析用） */
const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL'

/** 设计器新建画布的默认流程标识（process_1 之类），不宜直接当作业务编码使用 */
const DEFAULT_PROCESS_ID = /^[Pp]rocess_?\d*$/

const ASSIGNEE_TYPE_OPTIONS = [
  { value: ASSIGNEE_UNSET, label: '未配置' },
  { value: ASSIGNEE_USER, label: '指定人员' },
  { value: ASSIGNEE_ROLE, label: '指定角色' },
  { value: ASSIGNEE_LEADER, label: '部门主管' },
]

const FIELD_TYPE_OPTIONS = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'select', label: '下拉选择' },
]

const loading = ref(false)
const list = ref<FlowDefinitionVO[]>([])
const total = ref(0)
const query = reactive<FlowDefinitionPageQuery>({ pageNo: 1, pageSize: 10 })

/** 用户/角色下拉数据（审批人配置用） */
const userOptions = ref<UserVO[]>([])
const roleOptions = ref<RoleOptionVO[]>([])
/** 流程分类下拉数据：取自 flow_category 字典（`itemValue` 与 oa_flow_definition.category 同值） */
const categoryOptions = ref<DictDataVO[]>([])

// ---------------- 设计器抽屉 ----------------
const designerVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const modelerRef = ref<InstanceType<typeof BpmnModeler>>()
const currentXml = ref('')

const form = reactive<FlowDefinitionSaveRequest>({
  flowCode: '',
  flowName: '',
  category: '',
  status: 1,
  remark: '',
})

/** 节点审批人配置：key = 节点 id */
const nodeConfig = ref<NodeConfigItem[]>([])
/** 动态表单字段配置 */
const formFields = ref<FormFieldItem[]>([])

const rules: FormRules = {
  flowCode: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字、下划线', trigger: 'blur' },
  ],
  flowName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
}

/** 是否已有可发起的部署（决定提示文案） */
const hasDeployment = computed(() => Boolean(editingId.value))

async function loadPage() {
  loading.value = true
  try {
    const res = await getFlowPageApi(query)
    list.value = res.records
    total.value = res.total
  } catch (error) {
    logAsyncError('加载流程模板列表失败', error)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [users, roles, categories] = await Promise.all([
      getUserPageApi({ pageNo: 1, pageSize: 200 }),
      getRoleOptionsApi(),
      // 「分类」由字典驱动：新增一个分类只需在字典管理里加一条 flow_category 数据，无需改代码发版
      getDictDataByTypeApi('flow_category'),
    ])
    userOptions.value = users.records
    roleOptions.value = roles
    categoryOptions.value = categories
  } catch (error) {
    logAsyncError('加载审批人选项失败', error)
  }
}

function resetQuery() {
  query.flowName = undefined
  query.status = undefined
  query.category = undefined
  query.pageNo = 1
  void loadPage()
}

// ---------------- 导入 BPMN ----------------
const importVisible = ref(false)
const importing = ref(false)
const importFileName = ref('')
const importXml = ref('')
const importSummary = ref('')

const importForm = reactive({ flowCode: '', flowName: '', category: '' })

function openImport() {
  importVisible.value = true
  importFileName.value = ''
  importXml.value = ''
  importSummary.value = ''
  Object.assign(importForm, { flowCode: '', flowName: '', category: '' })
}

/**
 * 前端先读一遍 XML，用于预填流程编码/名称并提示节点数。
 * 这里只是「预填」——后端仍会用权威解析器重新解析，前端不做审批人判定。
 */
function parseBpmnMeta(xml: string) {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length > 0) {
    return null
  }
  const process = doc.getElementsByTagNameNS(BPMN_NS, 'process')[0]
  if (!process) {
    return null
  }
  return {
    key: process.getAttribute('id') ?? '',
    name: process.getAttribute('name') ?? '',
    nodeCount: doc.getElementsByTagNameNS(BPMN_NS, 'userTask').length,
  }
}

function handleImportFile(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) {
    return
  }
  importFileName.value = file.name
  void file.text().then((text) => {
    importXml.value = text
    const meta = parseBpmnMeta(text)
    if (!meta) {
      importSummary.value = '无法解析该文件，请确认是合法的 BPMN 2.0 XML'
      return
    }
    // 默认标识（process_1）没有业务含义，不预填而是提醒用户自己起名——
    // 流程编码保存后不可修改，直接沿用会让模板列表里出现一堆 process_1
    const isDefaultId = DEFAULT_PROCESS_ID.test(meta.key)
    if (!importForm.flowCode && !isDefaultId) {
      importForm.flowCode = meta.key
    }
    if (!importForm.flowName) {
      importForm.flowName = meta.name
    }
    importSummary.value = isDefaultId
      ? `文件用的是默认流程标识 ${meta.key}，请自行填写流程编码；用户任务 ${meta.nodeCount} 个`
      : `process id = ${meta.key}，用户任务 ${meta.nodeCount} 个`
  })
}

async function submitImport() {
  if (!importXml.value) {
    ElMessage.warning('请先选择 BPMN 文件')
    return
  }
  importing.value = true
  try {
    const id = await importFlowApi({
      bpmnXml: importXml.value,
      flowCode: importForm.flowCode.trim() || undefined,
      flowName: importForm.flowName.trim() || undefined,
      category: (importForm.category || '').trim() || undefined,
    })
    ElMessage.success('导入成功，流程已部署')
    importVisible.value = false
    await loadPage()
    // 直接进入设计器：导入只自动解析「已按约定标注」的审批人，其余需在此补配
    await openEdit(await getFlowDetailApi(id))
  } catch (error) {
    logAsyncError('导入 BPMN 失败', error)
  } finally {
    importing.value = false
  }
}

/** 解析模板里存的 JSON 配置，坏数据不影响页面可用 */
function parseJson<T>(json: string | undefined, fallback: T): T {
  if (!json) {
    return fallback
  }
  try {
    return JSON.parse(json) as T
  } catch {
    return fallback
  }
}

async function openCreate() {
  try {
    editingId.value = null
    Object.assign(form, {
      id: undefined,
      flowCode: '',
      flowName: '',
      category: '',
      status: 1,
      remark: '',
    })
    nodeConfig.value = []
    formFields.value = [{ field: 'reason', label: '申请事由', type: 'textarea', required: true }]
    currentXml.value = ''
    designerVisible.value = true
  } catch (error) {
    logAsyncError('准备流程模板表单失败', error)
  }
}

async function openEdit(row: FlowDefinitionVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    flowCode: row.flowCode,
    flowName: row.flowName,
    category: row.category ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  nodeConfig.value = parseJson<NodeConfigItem[]>(row.nodeConfig, [])
  // 归一化：历史模板可能缺 options，补齐后 el-select(multiple) 才不会绑定到 undefined
  formFields.value = parseJson<FormFieldItem[]>(row.formFields, []).map((f) => ({
    ...f,
    options: f.type === 'select' ? (Array.isArray(f.options) ? f.options : []) : f.options,
  }))
  try {
    currentXml.value = await getFlowBpmnApi(row.id)
  } catch {
    currentXml.value = ''
    ElMessage.warning('该模板暂无 BPMN 定义，请在设计器中重新绘制并保存')
  }
  designerVisible.value = true
}

/** 从当前画布同步节点列表，保留已配置项，剔除已删除的节点 */
function syncNodesFromCanvas() {
  const tasks = modelerRef.value?.getUserTasks() ?? []
  const existing = new Map(nodeConfig.value.map((item) => [item.nodeId, item]))
  nodeConfig.value = tasks.map((task) => {
    const old = existing.get(task.id)
    return old
      ? { ...old, nodeName: task.name }
      : { nodeId: task.id, nodeName: task.name, assigneeType: ASSIGNEE_LEADER, assigneeValue: '' }
  })
}

function addFormField() {
  formFields.value.push({ field: '', label: '', type: 'text', required: false, options: [] })
}

/** 历史模板可能没有 options 字段，切到下拉选择时补上空数组，避免 v-model 绑定 undefined */
function ensureOptions(row: FormFieldItem) {
  if (row.type === 'select' && !Array.isArray(row.options)) {
    row.options = []
  }
}

function removeFormField(index: number) {
  formFields.value.splice(index, 1)
}

/** 指定角色/人员时才需要填审批人值 */
function needAssigneeValue(type: number) {
  return type === ASSIGNEE_USER || type === ASSIGNEE_ROLE
}

async function save() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const xml = await modelerRef.value?.getXml()
  if (!xml) {
    ElMessage.warning('未能获取流程图内容，请检查设计器是否正常加载')
    return
  }

  const missingAssignee = nodeConfig.value.filter((n) => needAssigneeValue(n.assigneeType) && !n.assigneeValue)
  if (missingAssignee.length > 0) {
    ElMessage.warning(`节点「${missingAssignee[0].nodeName}」尚未指定审批人`)
    return
  }

  // 未配置的节点允许保存（便于分步配置），但要明确提示后果
  const unsetNodes = nodeConfig.value.filter((n) => n.assigneeType === ASSIGNEE_UNSET)
  if (unsetNodes.length > 0) {
    try {
      await ElMessageBox.confirm(
        `有 ${unsetNodes.length} 个节点未配置审批人（${unsetNodes.map((n) => n.nodeName).join('、')}），` +
          '申请发起后这些节点将无人可审批。确定继续保存吗？',
        '审批人未配置',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    await saveFlowApi({
      ...form,
      flowCode: form.flowCode.trim(),
      flowName: form.flowName.trim(),
      // 下拉清空后可能是 undefined/''，统一收敛为「未设置」
      category: (form.category || '').trim() || undefined,
      bpmnXml: xml,
      nodeConfig: JSON.stringify(nodeConfig.value),
      formFields: JSON.stringify(formFields.value),
    })
    ElMessage.success(editingId.value ? '修改成功，流程已重新部署' : '新增成功，流程已部署')
    designerVisible.value = false
    await loadPage()
  } finally {
    submitting.value = false
  }
}

async function remove(row: FlowDefinitionVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除流程模板「${row.flowName}」吗？该操作会同时卸载 Flowable 中的流程定义。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteFlowApi(row.id)
  ElMessage.success('删除成功')
  await loadPage()
}

async function toggleStatus(row: FlowDefinitionVO, enabled: boolean) {
  try {
    await updateFlowStatusApi(row.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '已启用' : '已停用')
    await loadPage()
  } catch (error) {
    logAsyncError('切换流程模板状态失败', error)
  }
}

onMounted(async () => {
  await loadOptions()
  await loadPage()
})
</script>

<template>
  <div class="flow-def">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="loadPage">
        <el-form-item label="流程名称">
          <el-input v-model="query.flowName" placeholder="请输入流程名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPage">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="flow-def__toolbar">
        <el-button v-permission="'system:flow:add'" type="primary" @click="openCreate">
          新增流程模板
        </el-button>
        <el-button v-permission="'system:flow:add'" @click="openImport">导入 BPMN</el-button>
        <span class="designer__muted">
          用任意 BPMN 工具画好后导入，或直接用内置样例（请假 / 报销）
        </span>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="flowCode" label="流程编码" width="160" />
        <el-table-column prop="flowName" label="流程名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bpmnKey" label="BPMN Key" width="160">
          <template #default="{ row }">{{ row.bpmnKey || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-permission="'system:flow:edit'"
              :model-value="row.status === 1"
              @change="(val: boolean) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">设计</el-button>
            <el-button v-permission="'system:flow:delete'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无流程模板" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="flow-def__pagination"
      />
    </el-card>

    <el-dialog
      v-model="designerVisible"
      :title="editingId ? '设计流程：' + form.flowName : '新增流程模板'"
      width="90%"
      top="4vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="designer">
        <div class="designer__left">
          <div class="designer__toolbar">
            <el-button size="small" @click="syncNodesFromCanvas">同步节点</el-button>
            <span class="designer__tip">绘制完成后点击「同步节点」再配置审批人</span>
          </div>
          <BpmnModeler ref="modelerRef" :xml="currentXml" />
        </div>

        <div class="designer__right">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="流程编码" prop="flowCode">
              <el-input
                v-model="form.flowCode"
                :disabled="hasDeployment"
                placeholder="如 leave_apply、expense_apply"
              />
              <div class="designer__muted">
                仅限字母、数字、下划线，保存并部署后不可修改。流程图里的流程标识会自动对齐为该编码，无需在画布中另外设置。
              </div>
            </el-form-item>
            <el-form-item label="流程名称" prop="flowName">
              <el-input v-model="form.flowName" placeholder="如 请假流程" />
            </el-form-item>
            <el-form-item label="分类">
              <!-- 分类由 flow_category 字典驱动，避免自由文本导致同一分类多种写法 -->
              <el-select v-model="form.category" placeholder="请选择分类" clearable style="width: 100%">
                <el-option
                  v-for="item in categoryOptions"
                  :key="item.id"
                  :label="item.itemLabel"
                  :value="item.itemValue"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>

          <el-divider content-position="left">节点审批人</el-divider>
          <el-table :data="nodeConfig" size="small" border max-height="220">
            <el-table-column prop="nodeName" label="节点" min-width="110" />
            <el-table-column label="规则" width="120">
              <template #default="{ row }">
                <el-select v-model="row.assigneeType" size="small">
                  <el-option
                    v-for="opt in ASSIGNEE_TYPE_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="审批人" min-width="130">
              <template #default="{ row }">
                <el-select
                  v-if="row.assigneeType === ASSIGNEE_USER"
                  v-model="row.assigneeValue"
                  size="small"
                  filterable
                  placeholder="选择人员"
                >
                  <el-option
                    v-for="u in userOptions"
                    :key="u.id"
                    :label="u.realName"
                    :value="String(u.id)"
                  />
                </el-select>
                <el-select
                  v-else-if="row.assigneeType === ASSIGNEE_ROLE"
                  v-model="row.assigneeValue"
                  size="small"
                  placeholder="选择角色"
                >
                  <el-option
                    v-for="r in roleOptions"
                    :key="r.id"
                    :label="r.roleName"
                    :value="String(r.id)"
                  />
                </el-select>
                <span v-else-if="row.assigneeType === ASSIGNEE_LEADER" class="designer__muted">
                  自动取发起人部门主管
                </span>
                <span v-else class="designer__warn">未配置审批人</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="请先绘制用户任务并同步节点" :image-size="50" />
            </template>
          </el-table>

          <el-divider content-position="left">
            申请表单字段
            <el-button link type="primary" size="small" @click="addFormField">添加字段</el-button>
          </el-divider>
          <el-table :data="formFields" size="small" border max-height="200">
            <el-table-column label="字段名" width="115">
              <template #default="{ row }">
                <el-input v-model="row.field" size="small" placeholder="如 days" />
              </template>
            </el-table-column>
            <el-table-column label="显示名" width="110">
              <template #default="{ row }">
                <el-input v-model="row.label" size="small" placeholder="如 请假天数" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="105">
              <template #default="{ row }">
                <el-select v-model="row.type" size="small" @change="ensureOptions(row)">
                  <el-option
                    v-for="opt in FIELD_TYPE_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <!-- 仅下拉选择需要配置候选项，其它类型保持窄列 -->
            <el-table-column label="候选项" width="170">
              <template #default="{ row }">
                <el-select
                  v-if="row.type === 'select'"
                  v-model="row.options"
                  size="small"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="输入后回车添加"
                  style="width: 100%"
                />
                <span v-else class="designer__muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="必填" width="60">
              <template #default="{ row }">
                <el-checkbox v-model="row.required" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="58">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeFormField($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无字段，发起申请时仅填标题" :image-size="50" />
            </template>
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="designerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存并部署</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importVisible"
      title="导入 BPMN 生成流程模板"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="import__tip"
        title="节点审批人按约定自动解析；未按约定标注的节点，导入后在设计器里补配即可"
        description="约定一（Flowable 标准属性）：flowable:candidateGroups=&quot;leader&quot; 部门主管，&quot;role:角色id&quot; 指定角色，flowable:candidateUsers=&quot;用户id&quot; 指定人员。约定二（扩展元素）：oa:assignee type=&quot;user|role|leader&quot; value=&quot;...&quot;。"
      />
      <el-form label-width="90px">
        <el-form-item label="BPMN 文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".bpmn,.xml"
            :on-change="handleImportFile"
          >
            <el-button>选择文件</el-button>
          </el-upload>
          <span v-if="importFileName" class="import__file">{{ importFileName }}</span>
        </el-form-item>
        <el-form-item label="流程编码">
          <el-input v-model="importForm.flowCode" placeholder="留空则取文件的 process id，建议手动填写" />
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="importForm.flowName" placeholder="留空取 BPMN 的 process name" />
        </el-form-item>
        <el-form-item label="分类">
          <!-- 同上：与 flow_category 字典同源，导入时不接受字典外的分类 -->
          <el-select v-model="importForm.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="解析结果">
          <span class="designer__muted">{{ importSummary || '请先选择 BPMN 文件' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入并部署</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.flow-def__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.import__tip {
  margin-bottom: 12px;
}

.import__file {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.flow-def__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.designer {
  display: flex;
  gap: 16px;
  height: 62vh;
}

.designer__left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.designer__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.designer__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.designer__right {
  width: 650px;
  flex-shrink: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.designer__muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.designer__warn {
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
