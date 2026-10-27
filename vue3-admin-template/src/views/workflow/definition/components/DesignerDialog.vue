<script setup lang="ts">
/**
 * 流程模板设计器弹窗（FE-OPT-05 从 index.vue 拆出）。
 *
 * <p>自包含：表单 / 节点配置 / 分支条件 / 表单绑定 / 字段权限 / 保存与发布校验
 * 全部在本组件内，对外只暴露 `openCreate / openEdit / openImport / preparePublish / close`，
 * 保存或导入成功后 emit `saved` 让列表页刷新。页面因此不再持有设计器状态。</p>
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormRules } from 'element-plus'
import BpmnModeler from '@/components/bpmn/BpmnModeler.vue'
import { getFlowBpmnApi, getFlowDetailApi } from '@/api/flow'
import { getRoleOptionsApi } from '@/api/role'
import { getDeptTreeApi } from '@/api/dept'
import { getUserCandidatesApi } from '@/api/user'
import { getFormOptionsApi } from '@/api/form'
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
import BranchConditionEditor from '../../components/BranchConditionEditor.vue'
import ImportBpmnDialog from './ImportBpmnDialog.vue'
import FormBindTab from './tabs/FormBindTab.vue'
import FieldPermTab from './tabs/FieldPermTab.vue'
import NodeBasicTab from './tabs/NodeBasicTab.vue'
import AssigneeTab from './tabs/AssigneeTab.vue'
import { ASSIGNEE_LEADER } from '../assigneeConstants'
import { useFlowConditions } from '../composables/useFlowConditions'
import { useNodeForms } from '../composables/useNodeForms'
import { useFieldPermission } from '../composables/useFieldPermission'
import { useDefinitionSave } from '../composables/useDefinitionSave'

defineProps<{
  /** 流程分类下拉（列表筛选项与设计器共用同一份字典，由页面统一加载） */
  categoryOptions: DictDataVO[]
}>()

const emit = defineEmits<{ saved: [] }>()

/** 保存 / 导入成功后让列表页刷新（本组件不持有列表状态） */
async function reloadList() {
  emit('saved')
}

/** 用户/角色/部门下拉数据（审批人配置用） */
const userOptions = ref<UserVO[]>([])
const roleOptions = ref<RoleOptionVO[]>([])
/** 部门树：「指定部门主管」策略选择固定部门的来源 */
const deptOptions = ref<DeptVO[]>([])

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

const importDialogRef = ref<InstanceType<typeof ImportBpmnDialog> | null>(null)

/** 打开导入弹窗：实际状态与解析都在 ImportBpmnDialog 内 */
function openImport() {
  importDialogRef.value?.open()
}

async function loadOptions() {
  try {
    const [users, roles, depts, forms] = await Promise.all([
      getUserCandidatesApi(),
      getRoleOptionsApi(),
      // 「指定部门主管」策略的部门来源：取全量部门树（含已停用部门，历史模板可能仍指向它）
      getDeptTreeApi(),
      // 节点表单绑定候选：只取启用态表单，停用表单不可绑定到新节点
      getFormOptionsApi(),
    ])
    userOptions.value = users
    roleOptions.value = roles
    deptOptions.value = depts
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
  reloadList,
})

/** 导入成功后：刷新列表 + 直接进入设计器补配审批人（导入只自动解析已按约定标注的） */
async function onImported(id: number) {
  await reloadList()
  await openEdit(await getFlowDetailApi(id))
}

/**
 * 发布前置准备：必要时先打开并加载该模板，同步节点后做发布校验。
 *
 * <p>发布校验依赖设计器内实时画布，因此必须由本组件提供；页面只拿返回值。</p>
 *
 * @returns 校验通过返回 null，否则返回提示文案
 */
async function preparePublish(row: FlowDefinitionVO): Promise<string | null> {
  if (!designerVisible.value || editingId.value !== row.id) {
    await openEdit(row)
  }
  syncNodesFromCanvas()
  return validatePublishable()
}

/** 关闭设计器（发布成功或页面侧需要收起时调用） */
function close() {
  designerVisible.value = false
}

onMounted(loadOptions)

defineExpose({ openCreate, openEdit, openImport, preparePublish, close })
</script>

<template>
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
</template>
