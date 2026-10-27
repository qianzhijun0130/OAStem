<script setup lang="ts">
/**
 * 流程模板管理：列表 + bpmn-js 可视化设计器 + 节点审批人配置 + 动态表单字段配置。
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus'
import BpmnModeler from '@/components/bpmn/BpmnModeler.vue'
import {
  deleteFlowApi,
  getFlowDetailApi,
  getFlowBpmnApi,
  updateFlowStatusApi,
} from '@/api/flow'
import { getRoleOptionsApi } from '@/api/role'
import { getDeptTreeApi } from '@/api/dept'
import { getDictDataByTypeApi } from '@/api/dict'
import { getUserCandidatesApi } from '@/api/user'
import {
  getFormOptionsApi,
} from '@/api/form'
import type {
  DeptVO,
  DictDataVO,
  FlowDefinitionSaveRequest,
  FlowDefinitionVO,
  FormFieldItem,
  NodeConfigItem,
  RoleOptionVO,
  UserVO,
} from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'
import BranchConditionEditor from '../components/BranchConditionEditor.vue'
import DefinitionTable from './components/DefinitionTable.vue'
import { useDefinitionList } from './composables/useDefinitionList'
import ImportBpmnDialog from './components/ImportBpmnDialog.vue'
import FormBindTab from './components/tabs/FormBindTab.vue'
import FieldPermTab from './components/tabs/FieldPermTab.vue'
import {
  ASSIGNEE_LEADER,
} from './assigneeConstants'
import NodeBasicTab from './components/tabs/NodeBasicTab.vue'
import { useFlowConditions } from './composables/useFlowConditions'
import { useNodeForms } from './composables/useNodeForms'
import { useFieldPermission } from './composables/useFieldPermission'
import { useDefinitionSave } from './composables/useDefinitionSave'
import AssigneeTab from './components/tabs/AssigneeTab.vue'

// 审批人规则常量与选项由 assigneeConstants.ts 提供：父级校验与 AssigneeTab 共用同一份，
// 避免「加了新规则能选但不被校验承认」的割裂


const importDialogRef = ref<InstanceType<typeof ImportBpmnDialog> | null>(null)

/** 打开导入弹窗：实际状态与解析都在 ImportBpmnDialog 内 */
function openImport() {
  importDialogRef.value?.open()
}

const {
  loading,
  list,
  total,
  query,
  loadPage,
  handleSizeChange,
  handlePageChange,
  resetQuery,
} = useDefinitionList()

/** 用户/角色/部门下拉数据（审批人配置用） */
const userOptions = ref<UserVO[]>([])
const roleOptions = ref<RoleOptionVO[]>([])
/** 部门树：「指定部门主管」策略选择固定部门的来源 */
const deptOptions = ref<DeptVO[]>([])
/** 流程分类下拉数据：取自 flow_category 字典（`itemValue` 与 oa_flow_definition.category 同值） */
const categoryOptions = ref<DictDataVO[]>([])

// ---------------- 设计器抽屉 ----------------
const designerVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const nodeBasicRef = ref<{ validate: () => Promise<boolean> } | null>(null)
const modelerRef = ref<InstanceType<typeof BpmnModeler>>()
const currentXml = ref('')

const form = reactive<FlowDefinitionSaveRequest>({
  flowCode: '',
  flowName: '',
  category: '',
  icon: '',
  status: 1,
  remark: '',
})

/** 节点审批人配置：key = 节点 id */
const nodeConfig = ref<NodeConfigItem[]>([])
/**
 * 动态表单字段配置（流程级）。
 *
 * <p><b>UI 已移除</b>：右侧面板的「申请表单字段」区块被「表单绑定」标签页取代 ——
 * 表单改为复用 {@code oa_form} 的 FormCreate 规则，字段清单由后端从规则派生，
 * 不再手工维护。</p>
 *
 * <p>但此变量**保留**：加载时回填、保存时原样回传。若一并删除，编辑保存会把历史
 * 模板的 {@code form_fields} 写成空数组，导致老数据丢失。彻底清空应等确认
 * 「存量 form_fields 已全部迁移到 oa_form」之后再做。</p>
 */
const formFields = ref<FormFieldItem[]>([])

/** 可选表单（已发布/启用的表单定义） */
const formOptions = ref<Array<{ id: number; formCode: string; formName: string }>>([])
/** 右侧面板当前标签页 */
const activeSideTab = ref('node')

/* ---------------- 分支条件配置 ----------------
 *
 * 数据流：画布（bpmn-js）↔ 结构化配置（flowConditions ↔ 后端 flow_config）。
 * 保存时后端把结构化条件注入 BPMN（只覆盖配置中出现的连线）；
 * 因此「删除条件 / 改为默认分支」必须同步清掉画布上的残留表达式，
 * 否则上次部署注入的表达式会在默认分支上继续生效。
 */

/** 属性面板宿主（第五标签页「流程属性」内的容器） */
const panelHost = ref<HTMLElement>()

/* 分支条件 / 排他网关的状态与操作在 composables/useFlowConditions.ts */
const {
  gatewayFlows,
  flowConditions,
  condGatewayId,
  editingFlow,
  flowVariables,
  canEditConditionRaw,
  currentGatewayFlows,
  condOf,
  isDefaultFlow,
  condSummaryOf,
  loadFlowConditions,
  refreshGatewayFlows,
  onCondGatewayChange,
  loadFlowVariables,
  editFlowCondition,
  commitEditingFlowCondition,
  setFlowAsDefault,
  removeFlowCondition,
  syncConditionsToCanvas,
  onConditionRawChanged,
} = useFlowConditions({
  editingId,
  parseJson,
  // 只暴露本模块用到的画布能力，避免把整个 modeler 实例交出去
  modeler: {
    getGatewayFlows: () => modelerRef.value?.getGatewayFlows() ?? [],
    clearFlowCondition: (flowId: string) => modelerRef.value?.clearFlowCondition(flowId),
  },
})

/* 节点↔表单绑定与字段权限在 composables/useNodeForms.ts / useFieldPermission.ts */
const { nodeFormBinds, bindableNodes, bindOf, loadNodeForms, onBindForm } = useNodeForms({
  editingId,
  nodeConfig,
  formOptions,
})

const {
  permNodeKey,
  permFields,
  savingPerm,
  permNodes,
  currentPermBind,
  onPermNodeChange,
  saveFieldPermissions,
} = useFieldPermission({ editingId, binds: nodeFormBinds, bindOf })

const rules: FormRules = {
  flowCode: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字、下划线', trigger: 'blur' },
  ],
  flowName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
}

/** 是否已有可发起的部署（决定提示文案） */
const hasDeployment = computed(() => Boolean(editingId.value))

async function loadOptions() {
  try {
    const [users, roles, depts, categories, forms] = await Promise.all([
      getUserCandidatesApi(),
      getRoleOptionsApi(),
      // 「指定部门主管」策略的部门来源：取全量部门树（含已停用部门，历史模板可能仍指向它）
      getDeptTreeApi(),
      // 「分类」由字典驱动：新增一个分类只需在字典管理里加一条 flow_category 数据，无需改代码发版
      getDictDataByTypeApi('flow_category'),
      // 节点表单绑定候选：只取启用态表单，停用表单不可绑定到新节点
      getFormOptionsApi(),
    ])
    userOptions.value = users
    roleOptions.value = roles
    deptOptions.value = depts
    categoryOptions.value = categories
    formOptions.value = forms.map((f) => ({ id: f.id, formCode: f.formCode, formName: f.formName }))
  } catch (error) {
    logAsyncError('加载审批人选项失败', error)
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
      icon: '',
      status: 1,
      remark: '',
    })
    nodeConfig.value = []
    flowConditions.value = []
    condGatewayId.value = ''
    editingFlow.value = null
    gatewayFlows.value = []
    flowVariables.value = { formFields: [], systemVars: [] }
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
    icon: row.icon ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  nodeConfig.value = parseJson<NodeConfigItem[]>(row.nodeConfig, [])
  // 归一化：历史模板可能缺 options，补齐后 el-select(multiple) 才不会绑定到 undefined
  formFields.value = parseJson<FormFieldItem[]>(row.formFields, []).map((f) => ({
    ...f,
    options: f.type === 'select' ? (Array.isArray(f.options) ? f.options : []) : f.options,
  }))
  loadFlowConditions(row)
  try {
    currentXml.value = await getFlowBpmnApi(row.id)
  } catch {
    currentXml.value = ''
    ElMessage.warning('该模板暂无 BPMN 定义，请在设计器中重新绘制并保存')
  }
  designerVisible.value = true
  // 等设计器把 BPMN 导入画布后再同步一次：
  // 既为已有节点补齐 nodeKind（历史数据没有该字段，发布校验要用），
  // 也顺带把「BPMN 里存在但 node_config 缺失」的节点补进来（如手工加的 create task）。
  await nextTick()
  syncNodesFromCanvas()
  // 绑定与表单选项依赖已保存的模板 id，新建的模板此刻还没有 id，跳过即可
  if (editingId.value) {
    await loadNodeForms(editingId.value)
    await loadFlowVariables()
  }
}

/**
 * 从当前画布同步节点列表，保留已配置项，剔除已删除的节点。
 *
 * <p>采集范围含<b>用户任务与创建任务</b>（bpmn-js 里分别是 {@code bpmn:UserTask} 与
 * {@code bpmn:Task}）。此前只采前者，导致 create task 节点不进入 {@code node_config}，
 * 而后端以 {@code node_config} 为审批人唯一依据 → 该节点在待办里无人可办。</p>
 */
function syncNodesFromCanvas() {
  const tasks = modelerRef.value?.getUserTasks() ?? []
  const existing = new Map(nodeConfig.value.map((item) => [item.nodeId, item]))
  nodeConfig.value = tasks.map((task) => {
    const old = existing.get(task.id)
    return old
      ? { ...old, nodeName: task.name, nodeKind: task.kind }
      : {
          nodeId: task.id,
          nodeName: task.name,
          // 新节点默认「部门主管」：多数审批场景适用，且用户可改
          assigneeType: ASSIGNEE_LEADER,
          assigneeValue: '',
          nodeKind: task.kind,
        }
  })
  // 网关出线同步刷新：新画的网关要立刻出现在「分支条件」标签页里
  refreshGatewayFlows()
}

/**
 * 画布结构变化后的自动刷新（防抖）。
 *
 * <p>事件来源：{@code shape.added / shape.removed / connection.added / connection.removed /
 * element.changed}。拖拽或连续操作会产生事件洪流，故合并到 300ms 后统一刷新一次。</p>
 *
 * <p><b>为什么只刷节点与网关出线、不刷表单绑定列表</b>：表单绑定页的候选节点是
 * {@code nodeConfig} 的计算属性，节点清单更新后它会自动跟随，无需单独刷新；
 * 而真正需要网络请求的「已绑定关系」只在模板 id 变化时加载一次。</p>
 */
let syncTimer: ReturnType<typeof setTimeout> | null = null
function onCanvasElementsChanged() {
  if (syncTimer) {
    clearTimeout(syncTimer)
  }
  syncTimer = setTimeout(() => {
    syncTimer = null
    syncNodesFromCanvas()
  }, 300)
}




/* 保存与发布校验在 composables/useDefinitionSave.ts（两者共用同一套审批人配齐判定） */
const { save, validatePublishable } = useDefinitionSave({
  form,
  editingId,
  submitting,
  designerVisible,
  nodeConfig,
  formFields,
  flowConditions,
  nodeFormBinds,
  gatewayFlows,
  validateForm: () => nodeBasicRef.value?.validate(),
  // 现场取 modeler：设计器是弹窗，未打开时 modelerRef 为空
  canvas: () => {
    const modeler = modelerRef.value
    return modeler ? { getXml: () => modeler.getXml() } : null
  },
  syncNodes: syncNodesFromCanvas,
  syncConditions: syncConditionsToCanvas,
  reloadList: loadPage,
})

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

/** 流程模板状态：0草稿/未发布 1已发布（复用 oa_flow_definition.status，无 DDL） */
const FLOW_STATUS_DRAFT = 0
const FLOW_STATUS_PUBLISHED = 1

/**
 * 发布流程模板。
 *
 * <p>重复操作防护：已发布模板不显示发布按钮，此处再兜一层，
 * 避免并发点击或旧页面状态导致重复请求。</p>
 */
async function publishFlow(row: FlowDefinitionVO) {
  if (row.status === FLOW_STATUS_PUBLISHED) {
    ElMessage.info('该模板已发布')
    return
  }
  // 校验依赖设计器内实时画布；若当前未在设计器中编辑该模板，
  // 先打开并加载最新画布（openEdit 会挂载 modeler + syncNodesFromCanvas），
  // 加载完成后继续做同步与校验，不再提前 return，
  // 避免「要求先手动点同步节点」导致的发布死循环。
  if (!designerVisible.value || editingId.value !== row.id) {
    await openEdit(row)
  }
  syncNodesFromCanvas()
  const err = validatePublishable()
  if (err) {
    ElMessage.warning(err)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认发布流程「${row.flowName}」？发布后该模板将出现在员工的「发起申请」下拉中。`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await applyStatus(row, FLOW_STATUS_PUBLISHED, '已发布')
  designerVisible.value = false
  await loadPage()
}

/** 下架流程模板：确认后立即生效，员工侧下拉不再显示 */
async function unpublishFlow(row: FlowDefinitionVO) {
  if (row.status !== FLOW_STATUS_PUBLISHED) {
    ElMessage.info('该模板未处于发布状态')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认下架流程「${row.flowName}」？下架后员工将无法再发起该流程，已发起的审批单不受影响。`,
      '下架确认',
      { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await applyStatus(row, FLOW_STATUS_DRAFT, '已下架')
}

/** 统一切换状态：就地改行数据，避免整表重拉（与项目「保存不重载」约定一致） */
async function applyStatus(row: FlowDefinitionVO, status: number, tip: string) {
  try {
    await updateFlowStatusApi(row.id, status)
    row.status = status
    ElMessage.success(tip)
  } catch (error) {
    logAsyncError('切换流程模板状态失败', error)
  }
}

/** 状态展示：草稿 / 已发布 */

/** 导入成功后：刷新列表 + 直接进入设计器补配审批人（导入只自动解析已按约定标注的） */
async function onImported(id: number) {
  await loadPage()
  await openEdit(await getFlowDetailApi(id))
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

      <DefinitionTable
        :list="list"
        :loading="loading"
        @edit="openEdit"
        @publish="publishFlow"
        @unpublish="unpublishFlow"
        @remove="remove"
      />

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="flow-def__pagination"
      />
    </el-card>

    <ResponsiveDialog
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
            <span class="designer__tip">节点已自动同步，此按钮仅作手动兜底</span>
          </div>
          <BpmnModeler
            ref="modelerRef"
            :xml="currentXml"
            :panel-host="panelHost"
            :condition-raw-editable="canEditConditionRaw"
            @condition-raw-changed="onConditionRawChanged"
            @elements-changed="onCanvasElementsChanged"
          />
        </div>

<!-- 右侧面板：标签页组织 -->
<div class="designer__right">
  <el-tabs v-model="activeSideTab" class="designer__tabs">
    <!-- 标签 1：节点基本 -->
    <el-tab-pane label="节点基本" name="node">
      <NodeBasicTab
        ref="nodeBasicRef"
        v-model:flow-code="form.flowCode"
        v-model:flow-name="form.flowName"
        v-model:category="form.category"
        v-model:icon="form.icon"
        v-model:status="form.status"
        v-model:remark="form.remark"
        :rules="rules"
        :has-deployment="hasDeployment"
        :category-options="categoryOptions"
        :node-config="nodeConfig"
      />
    </el-tab-pane>

    <!-- 标签 2：审批人配置 -->
    <el-tab-pane label="审批人配置" name="assignee">
      <AssigneeTab
        :nodes="nodeConfig"
        :user-options="userOptions"
        :role-options="roleOptions"
        :dept-options="deptOptions"
      />
    </el-tab-pane>

    <!-- 标签 3：表单绑定 -->
    <el-tab-pane label="表单绑定" name="form">
      <FormBindTab
        :nodes="bindableNodes"
        :form-options="formOptions"
        :binds="nodeFormBinds"
        @bind="onBindForm"
      />
    </el-tab-pane>

    <!-- 标签 4：字段权限 -->
    <el-tab-pane label="字段权限" name="field">
      <FieldPermTab
        v-model:node-key="permNodeKey"
        :node-options="permNodes"
        :form-name="currentPermBind?.formName ?? null"
        :fields="permFields"
        :saving="savingPerm"
        @change="onPermNodeChange"
        @save="saveFieldPermissions"
      />
    </el-tab-pane>

    <!-- 标签 5：流程属性（bpmn-js 官方属性面板挂载点，选中画布元素后展示其标准属性） -->
    <el-tab-pane label="流程属性" name="property">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="画布元素的标准属性"
        description="在左侧画布选中节点或连线后，这里展示该元素的通用属性。流程编码、名称、分类与状态请到「节点基本」标签页维护（两处同源，保存时以画布同步结果为准）；节点 ID 是系统关联键，只读不可修改。"
        class="designer__alert"
      />
      <div ref="panelHost" class="designer__panel-host" />
      <div class="designer__muted designer__muted--block">
        选中排他网关的出线时，「分支条件」分组可查看该连线的条件表达式原文；
        是否能编辑由权限码 system:flow:condition-raw 决定（默认仅超管可改）。
        在此改动原文后，该连线的「分支条件」结构化配置会被作废，改由原文管理。
      </div>
    </el-tab-pane>

    <!-- 标签 6：分支条件（排他网关的判定条件与默认分支） -->
    <el-tab-pane label="分支条件" name="condition">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="条件只对排他网关（菱形）生效"
        description="引擎按连线顺序逐个求值，第一条满足的连线被采用，因此条件之间隐含「先到先得」；每个网关必须有一条默认分支兜底，否则所有条件都不满足时流程会卡住。保存时这里的条件会写入流程图，覆盖上次部署生成的表达式。"
        class="designer__alert"
      />
      <div class="designer__cond-bar">
        <span class="designer__muted">排他网关</span>
        <el-select
          v-model="condGatewayId"
          placeholder="请选择网关"
          style="width: 240px"
          @change="onCondGatewayChange"
        >
          <el-option
            v-for="g in gatewayFlows"
            :key="g.gatewayId"
            :label="g.gatewayName || g.gatewayId"
            :value="g.gatewayId"
          />
        </el-select>
        <span v-if="!gatewayFlows.length" class="designer__warn">
          画布上还没有排他网关，绘制后会自动出现在这里
        </span>
      </div>
      <el-table v-if="condGatewayId" :data="currentGatewayFlows" class="designer__cond-table" size="small" border max-height="260">
        <el-table-column label="去向" min-width="110">
          <template #default="{ row }">
            {{ row.targetNodeName || row.targetNodeId }}
          </template>
        </el-table-column>
        <el-table-column label="分支规则" min-width="220">
          <template #default="{ row }">
            <template v-if="isDefaultFlow(row.flowId)">
              <el-tag type="info" size="small">默认分支</el-tag>
              <span class="designer__muted">其余条件都不满足时走这里</span>
            </template>
            <template v-else-if="condOf(row.flowId)">
              <div v-if="condOf(row.flowId)?.name" class="designer__cond-name">
                {{ condOf(row.flowId)?.name }}
              </div>
              <span class="designer__muted">{{ condSummaryOf(row.flowId) }}</span>
            </template>
            <span v-else class="designer__warn">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button icon-only
              v-if="!isDefaultFlow(row.flowId)"
              link
              type="primary"
              size="small"
              @click="editFlowCondition({ flowId: row.flowId, gatewayId: condGatewayId, targetNodeId: row.targetNodeId, targetNodeName: row.targetNodeName || row.targetNodeId })"
            >
              {{ condOf(row.flowId) ? '编辑条件' : '配置条件' }}
            </el-button>
            <el-button icon-only
              v-if="!isDefaultFlow(row.flowId)"
              link
              type="warning"
              size="small"
              @click="setFlowAsDefault({ flowId: row.flowId, gatewayId: condGatewayId, targetNodeName: row.targetNodeName || row.targetNodeId })"
            >
              设为默认
            </el-button>
            <el-button icon-only
              v-if="condOf(row.flowId) && !isDefaultFlow(row.flowId)"
              link
              type="danger"
              size="small"
              @click="removeFlowCondition({ flowId: row.flowId, targetNodeName: row.targetNodeName || row.targetNodeId })"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该网关暂无出线" :image-size="50" />
        </template>
      </el-table>
      <div v-else-if="gatewayFlows.length" class="designer__muted designer__muted--block">
        请先选择一个排他网关。
      </div>

      <!-- 条件编辑区：交给独立组件处理（基础信息 + 模式切换 + 条件组树） -->
      <div v-if="editingFlow" class="designer__cond-editor">
        <el-divider content-position="left">
          编辑条件：通往「{{ editingFlow.targetNodeName || editingFlow.targetNodeId }}」
        </el-divider>
        <!-- key 绑定 flowId：切换编辑对象时让编辑器重新挂载，草稿随之重置 -->
        <BranchConditionEditor
          :key="editingFlow.flowId"
          :item="editingFlow"
          :variables="flowVariables"
          :raw-editable="canEditConditionRaw"
          @update:item="editingFlow = $event"
        />
        <div class="designer__cond-actions">
          <el-button type="primary" size="small" @click="commitEditingFlowCondition">确定</el-button>
          <el-button size="small" @click="editingFlow = null">取消</el-button>
        </div>
      </div>
      <div v-if="condGatewayId" class="designer__muted designer__muted--block">
        变量取自「发起环节主表单字段 + 系统内置变量」两组；条件在流程变量上求值，
        申请内容在发起时已平铺为同名变量。数值按数字比较，日期与文本按字典序比较，
        布尔取 true / false。保存时后端会校验变量名合法性并做表达式安全扫描。
      </div>
    </el-tab-pane>
  </el-tabs>
</div>
      </div>

      <template #footer>
        <el-button @click="designerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存</el-button>
      </template>
    </ResponsiveDialog>

    <ImportBpmnDialog ref="importDialogRef" :category-options="categoryOptions" @imported="onImported" />
  </div>
</template>

<style src="./definition.css"></style>
