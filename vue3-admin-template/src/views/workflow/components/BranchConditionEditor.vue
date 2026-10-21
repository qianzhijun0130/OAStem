<script setup lang="ts">
/**
 * 分支条件编辑器（「分支条件」标签页内编辑单条出线）。
 *
 * <p>负责四件事：① 分支基础信息（名称 / 描述）；② 配置模式切换（可视化 / 手写 EL）；
 * ③ 可视化条件组的增删（支持 AND / OR 嵌套一层）；④ 比较值控件按变量类型联动。</p>
 *
 * <p><b>为什么独立成组件</b>：流程模板页已经承载了列表、设计器、审批人、表单绑定、字段权限
 * 五块逻辑，条件树再加进来会让它膨胀到难以维护。这里只接收一条分支配置，改动通过
 * {@code update:item} 上抛，父组件不感知内部结构。</p>
 *
 * <p><b>状态约定</b>：组件在 setup 时从 props 深拷贝一份草稿，之后所有编辑都发生在草稿上。
 * 父组件切换编辑对象时必须用 {@code :key} 让它重新挂载，否则草稿不会跟着换。</p>
 */
import { computed, ref } from 'vue'
import type {
  ConditionGroup,
  ConditionValueType,
  FlowConditionItem,
  FlowConditionRule,
  FlowVariableVO,
} from '@/api/types'

const props = defineProps<{
  /** 正在编辑的分支配置 */
  item: FlowConditionItem
  /** 可选变量清单（表单字段 + 系统内置变量） */
  variables: FlowVariableVO
  /** 是否允许手写 EL 表达式（权限码 system:flow:condition-raw） */
  rawEditable: boolean
}>()

const emit = defineEmits<{ (e: 'update:item', value: FlowConditionItem): void }>()

/** 与后端 BpmnXmlNormalizer.MAX_RULES_PER_FLOW 保持一致 */
const MAX_RULES = 20

/** 无需比较值的运算符 */
const VALUELESS_OPS = new Set(['empty', 'notEmpty'])

/** 运算符按取值类型分组：不在组内的运算符不会被提供给用户，后端也会拒绝 */
const OPS_BY_TYPE: Record<ConditionValueType, Array<{ value: string; label: string }>> = {
  number: [
    { value: 'eq', label: '=' },
    { value: 'ne', label: '≠' },
    { value: 'gt', label: '>' },
    { value: 'ge', label: '≥' },
    { value: 'lt', label: '<' },
    { value: 'le', label: '≤' },
    { value: 'empty', label: '为空' },
    { value: 'notEmpty', label: '非空' },
  ],
  string: [
    { value: 'eq', label: '=' },
    { value: 'ne', label: '≠' },
    { value: 'gt', label: '>' },
    { value: 'ge', label: '≥' },
    { value: 'lt', label: '<' },
    { value: 'le', label: '≤' },
    { value: 'contains', label: '包含' },
    { value: 'notContains', label: '不包含' },
    { value: 'empty', label: '为空' },
    { value: 'notEmpty', label: '非空' },
  ],
  date: [
    { value: 'eq', label: '=' },
    { value: 'ne', label: '≠' },
    { value: 'gt', label: '晚于' },
    { value: 'ge', label: '不早于' },
    { value: 'lt', label: '早于' },
    { value: 'le', label: '不晚于' },
    { value: 'empty', label: '为空' },
    { value: 'notEmpty', label: '非空' },
  ],
  boolean: [
    { value: 'eq', label: '=' },
    { value: 'ne', label: '≠' },
  ],
}

const LOGIC_OPTIONS = [
  { value: 'and', label: '且（全部满足）' },
  { value: 'or', label: '或（任一满足）' },
]

/** 变量下拉：按「表单字段 / 系统变量」分组，未绑定表单时只剩系统变量 */
const variableOptions = computed(() => [
  {
    label: '申请表单字段',
    options: props.variables.formFields.map((f) => ({ ...f, value: f.key })),
  },
  {
    label: '系统内置变量',
    options: props.variables.systemVars.map((f) => ({ ...f, value: f.key })),
  },
])

/** 变量 key → 取值类型，选完变量后据此联动比较值控件 */
const typeByKey = computed(() => {
  const map = new Map<string, ConditionValueType>()
  for (const f of props.variables.formFields) {
    map.set(f.key, f.type)
  }
  for (const f of props.variables.systemVars) {
    map.set(f.key, f.type)
  }
  return map
})

/**
 * 深拷贝一份草稿：编辑全部发生在草稿上，确认时整体回传。
 *
 * <p><b>为什么不用 {@code structuredClone}</b>：props 传入的是 Vue 响应式 Proxy，
 * 而 structuredClone 遇到 Proxy 会抛 {@code DataCloneError}——那会让 setup 直接失败，
 * 整个编辑器退化成空节点（界面上表现为点了「配置条件」却什么都没有）。</p>
 *
 * <p>这里改用 JSON 往返：草稿本身就是纯配置数据（最终要序列化进 flow_config），
 * 既天然免疫 Proxy，也与落库形态保持一致。若将来草稿里出现 Date / Map 等
 * 不可 JSON 化的字段，需要同步换成带类型的递归拷贝。</p>
 */
function cloneDraft(item: FlowConditionItem): FlowConditionItem {
  return JSON.parse(JSON.stringify(item)) as FlowConditionItem
}

const draft = ref<FlowConditionItem>(cloneDraft(props.item))

/** 根条件组（不存在则现场建一个空组） */
const root = computed<ConditionGroup>(() => {
  if (!draft.value.condition) {
    draft.value.condition = { logic: 'and', rules: [], groups: [] }
  }
  const g = draft.value.condition
  if (!g.rules) {
    g.rules = []
  }
  if (!g.groups) {
    g.groups = []
  }
  return g
})

const mode = computed<'visual' | 'raw'>(() =>
  draft.value.mode === 'raw' ? 'raw' : 'visual',
)

/** 当前已配置的条件总数（含子组），用于「+ 添加」按钮的可用状态 */
const ruleCount = computed(() => {
  const g = draft.value.condition
  if (!g) {
    return 0
  }
  const inGroup = (group: ConditionGroup): number =>
    (group.rules?.length ?? 0) +
    (group.groups ?? []).reduce((sum, sub) => sum + inGroup(sub), 0)
  return inGroup(g)
})

function emitChange() {
  emit('update:item', draft.value)
}

/** 切换配置模式：另一侧的值一并清空，避免「界面显示一套、实际生效另一套」 */
function onModeChange(next: 'visual' | 'raw') {
  draft.value.mode = next
  if (next === 'raw') {
    draft.value.condition = { logic: 'and', rules: [], groups: [] }
  } else {
    draft.value.expression = ''
  }
  emitChange()
}

function newRule(): FlowConditionRule {
  return { field: '', op: 'eq', valueType: 'string', value: '' }
}

function addRule(group: ConditionGroup) {
  if (!group.rules) {
    group.rules = []
  }
  group.rules.push(newRule())
  emitChange()
}

function removeRule(group: ConditionGroup, index: number) {
  group.rules?.splice(index, 1)
  emitChange()
}

function addGroup() {
  if (!root.value.groups) {
    root.value.groups = []
  }
  root.value.groups.push({ logic: 'or', rules: [newRule()], groups: [] })
  emitChange()
}

function removeGroup(index: number) {
  root.value.groups?.splice(index, 1)
  emitChange()
}

/**
 * 选中变量后：同步取值类型，并修正不兼容的运算符。
 *
 * <p>例如先按「文本」选了「包含」，再换成数字类型的变量，此时「包含」对数字无意义，
 * 自动回退为「等于」，避免出现「界面能选、保存被后端拒」的割裂。</p>
 */
function onFieldChange(rule: FlowConditionRule) {
  const type = typeByKey.value.get(rule.field) ?? 'string'
  rule.valueType = type
  const allowed = OPS_BY_TYPE[type].map((o) => o.value)
  if (!allowed.includes(rule.op)) {
    rule.op = 'eq'
  }
  if (VALUELESS_OPS.has(rule.op)) {
    rule.value = ''
  }
  emitChange()
}

function onOpChange(rule: FlowConditionRule) {
  if (VALUELESS_OPS.has(rule.op)) {
    rule.value = ''
  }
  emitChange()
}

function opsOf(rule: FlowConditionRule) {
  const type: ConditionValueType = rule.valueType ?? 'string'
  return OPS_BY_TYPE[type] ?? OPS_BY_TYPE.string
}

function isValueless(op: string) {
  return VALUELESS_OPS.has(op)
}
</script>

<template>
  <div class="cond-editor">
    <div class="cond-editor__row">
      <span class="cond-editor__label">分支名称</span>
      <el-input
        v-model="draft.name"
        size="small"
        placeholder="如：大额审批（用于列表与流转日志展示）"
        @input="emitChange"
      />
    </div>
    <div class="cond-editor__row">
      <span class="cond-editor__label">分支描述</span>
      <el-input
        v-model="draft.description"
        type="textarea"
        :rows="2"
        size="small"
        placeholder="给后来维护的人说明这条分支的用途"
        @input="emitChange"
      />
    </div>

    <div class="cond-editor__row">
      <span class="cond-editor__label">配置模式</span>
      <el-radio-group :model-value="mode" size="small" @change="onModeChange">
        <el-radio-button value="visual">可视化条件</el-radio-button>
        <el-radio-button value="raw" :disabled="!rawEditable">手写表达式</el-radio-button>
      </el-radio-group>
      <span v-if="!rawEditable" class="cond-editor__hint">手写表达式需要「条件原文编辑」权限</span>
    </div>

    <!-- 手写 EL 表达式 -->
    <template v-if="mode === 'raw'">
      <el-input
        v-model="draft.expression"
        type="textarea"
        :rows="3"
        size="small"
        placeholder="如 ${days > 3 &amp;&amp; type == '出差'}"
        @input="emitChange"
      />
      <div class="cond-editor__hint">
        表达式需符合 JUEL 语法；保存时后端会做安全校验（禁止反射调用等危险写法）
      </div>
    </template>

    <!-- 可视化条件组 -->
    <template v-else>
      <div class="cond-editor__group">
        <div class="cond-editor__group-head">
          <el-select v-model="root.logic" size="small" style="width: 150px" @change="emitChange">
            <el-option v-for="l in LOGIC_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
          <span class="cond-editor__hint">以下条件{{ root.logic === 'or' ? '任一' : '全部' }}满足时走这条分支</span>
        </div>

        <div v-for="(rule, index) in root.rules" :key="'r' + index" class="cond-editor__rule">
          <el-select
            v-model="rule.field"
            size="small"
            filterable
            placeholder="选择变量"
            style="width: 190px"
            @change="onFieldChange(rule)"
          >
            <el-option-group
              v-for="grp in variableOptions"
              :key="grp.label"
              :label="grp.label"
            >
              <el-option
                v-for="opt in grp.options"
                :key="opt.value"
                :label="`${opt.label}（${opt.key}）`"
                :value="opt.value"
              />
            </el-option-group>
          </el-select>
          <el-select
            v-model="rule.op"
            size="small"
            style="width: 110px"
            @change="onOpChange(rule)"
          >
            <el-option v-for="o in opsOf(rule)" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input-number
            v-if="rule.valueType === 'number' && !isValueless(rule.op)"
            v-model="rule.value"
            size="small"
            style="width: 130px"
            :controls="false"
            @change="emitChange"
          />
          <el-date-picker
            v-else-if="rule.valueType === 'date' && !isValueless(rule.op)"
            v-model="rule.value"
            type="date"
            size="small"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 150px"
            @change="emitChange"
          />
          <el-select
            v-else-if="rule.valueType === 'boolean' && !isValueless(rule.op)"
            v-model="rule.value"
            size="small"
            style="width: 110px"
            @change="emitChange"
          >
            <el-option label="真" value="true" />
            <el-option label="假" value="false" />
          </el-select>
          <el-input
            v-else-if="!isValueless(rule.op)"
            v-model="rule.value"
            size="small"
            placeholder="比较值"
            style="width: 150px"
            @input="emitChange"
          />
          <el-button link type="danger" size="small" @click="removeRule(root, index)">删除</el-button>
        </div>

        <!-- 子条件组：产品层限制一层，子组内不再提供「添加条件组」 -->
        <div v-for="(sub, gi) in root.groups" :key="'g' + gi" class="cond-editor__sub">
          <div class="cond-editor__sub-head">
            <el-select v-model="sub.logic" size="small" style="width: 130px" @change="emitChange">
              <el-option v-for="l in LOGIC_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
            </el-select>
            <span class="cond-editor__hint">子条件组</span>
            <el-button link type="danger" size="small" @click="removeGroup(gi)">删除分组</el-button>
          </div>
          <div v-for="(rule, index) in sub.rules" :key="'sr' + index" class="cond-editor__rule">
            <el-select
              v-model="rule.field"
              size="small"
              filterable
              placeholder="选择变量"
              style="width: 190px"
              @change="onFieldChange(rule)"
            >
              <el-option-group
                v-for="grp in variableOptions"
                :key="grp.label"
                :label="grp.label"
              >
                <el-option
                  v-for="opt in grp.options"
                  :key="opt.value"
                  :label="`${opt.label}（${opt.key}）`"
                  :value="opt.value"
                />
              </el-option-group>
            </el-select>
            <el-select
              v-model="rule.op"
              size="small"
              style="width: 110px"
              @change="onOpChange(rule)"
            >
              <el-option v-for="o in opsOf(rule)" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input-number
              v-if="rule.valueType === 'number' && !isValueless(rule.op)"
              v-model="rule.value"
              size="small"
              style="width: 130px"
              :controls="false"
              @change="emitChange"
            />
            <el-date-picker
              v-else-if="rule.valueType === 'date' && !isValueless(rule.op)"
              v-model="rule.value"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 150px"
              @change="emitChange"
            />
            <el-select
              v-else-if="rule.valueType === 'boolean' && !isValueless(rule.op)"
              v-model="rule.value"
              size="small"
              style="width: 110px"
              @change="emitChange"
            >
              <el-option label="真" value="true" />
              <el-option label="假" value="false" />
            </el-select>
            <el-input
              v-else-if="!isValueless(rule.op)"
              v-model="rule.value"
              size="small"
              placeholder="比较值"
              style="width: 150px"
              @input="emitChange"
            />
            <el-button link type="danger" size="small" @click="removeRule(sub, index)">删除</el-button>
          </div>
          <el-button
            link
            type="primary"
            size="small"
            :disabled="ruleCount >= MAX_RULES"
            @click="addRule(sub)"
          >
            + 添加条件
          </el-button>
        </div>

        <div class="cond-editor__actions">
          <el-button link type="primary" size="small" :disabled="ruleCount >= MAX_RULES" @click="addRule(root)">
            + 添加条件
          </el-button>
          <el-button link type="primary" size="small" :disabled="ruleCount >= MAX_RULES" @click="addGroup">
            + 添加条件组
          </el-button>
          <span class="cond-editor__hint">已配 {{ ruleCount }} / {{ MAX_RULES }} 条，条件组最多嵌套一层</span>
        </div>
      </div>

      <div class="cond-editor__hint">
        提示：引擎按上方出线列表的顺序判断分支，命中第一条满足条件的分支；都不满足时走「默认分支」。
      </div>
    </template>
  </div>
</template>

<style scoped>
.cond-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cond-editor__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cond-editor__label {
  width: 68px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.cond-editor__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cond-editor__group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
  background: var(--el-fill-color-lighter);
}

.cond-editor__group-head,
.cond-editor__sub-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.cond-editor__rule {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.cond-editor__sub {
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  padding: 6px 8px;
  margin-bottom: 6px;
  background: var(--el-bg-color);
}

.cond-editor__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
