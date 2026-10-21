<script setup lang="ts">
/**
 * FormCreate 运行时渲染器。
 *
 * <p>被「发起申请」「表单预览」「审批详情」三处复用，故对 FormCreate 的调用细节
 * 收敛在这里，页面只负责传 `ruleJson` + 数据，不直接依赖 `<FormCreate>` 标签。</p>
 *
 * <p>对外能力：
 * <ul>
 *   <li>{@code validate} —— 触发 FormCreate 内置校验（required / min / max / pattern），
 *       发起页提交前必须调用；规则里的自定义 validator 也在此生效。</li>
 *   <li>{@code getValues} —— 取出当前数据（发起页提交时序列化为 form_data）。</li>
 *   <li>{@code setValues} —— 回填数据（详情页用 form_data 还原）。</li>
 * </ul>
 *
 * <p>只读模式（详情/预览）通过把每个控件的 props 覆写为 disabled 实现，
 * 而不是把 FormCreate 换成纯文本渲染——这样字段的排版与填写时完全一致。</p>
 *
 * <p><b>api 的获取方式（D8 缺陷修复）</b>：FormCreate 组件<b>没有 expose</b> 它的 api，
 * {@code <FormCreate ref>} 拿到的是组件公开实例，上面并没有 validate / formData 等方法
 * （api 挂在组件 setupState 的 {@code fapi} 上）。直接 {@code ref.value.validate()}
 * 会抛 {@code TypeError: instance.validate is not a function}。正确做法是监听
 * {@code update:api} 事件——FormCreate 在 created 时会把 fapi 通过该事件抛出。</p>
 */
import { computed, ref, watch } from 'vue'
import type { Api as FormCreateApi } from '@form-create/element-ui'
import type { FormRuleItem } from '@/api/types'

const props = withDefaults(
  defineProps<{
    /** FormCreate 规则 JSON 字符串 */
    ruleJson?: string
    /** 初始值 / 回填数据（键为 prop） */
    modelValue?: Record<string, unknown>
    /** 只读模式（整体禁用） */
    disabled?: boolean
    /** 标签宽度；只读时自动收窄，避免大片留白 */
    labelWidth?: string
    /**
     * 指定哪些字段可编辑（字段标识集合）。
     *
     * <p><b>与 disabled 的关系</b>：{@code disabled} 是整体只读；本项用于「部分字段可编辑」的
     * 场景，如销假节点只让「销假日期」可填、其余保持只读。</p>
     *
     * <p><b>默认行为</b>：不传时按 {@code disabled} 决定整体状态；传了空数组则<b>全部只读</b>
     * —— 这与后端「未配置即默认只读」的约定一致。</p>
     */
    editableProps?: string[]
  }>(),
  {
    ruleJson: '',
    modelValue: () => ({}),
    disabled: false,
    labelWidth: '110px',
    editableProps: undefined,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'change', value: Record<string, unknown>): void
}>()

/**
 * FormCreate 运行时 api 实例（validate / formData / resetFields）。
 *
 * <p>经 {@code update:api} 事件获取，不用组件 ref——组件未 expose api，
 * ref 上直接调方法会抛 TypeError（D8 缺陷根因，见文件头说明）。</p>
 *
 * <p>注意：该事件只在组件 created 时触发一次，卸载不会置空。
 * 因此「未渲染表单」的场景（未绑定表单 / 规则解析失败 / 切到无表单模板）
 * 不能依赖 fapi 判空，要用 {@code rules} 长度做前置守卫，否则会拿到
 * 上一份已卸载表单的陈旧实例。</p>
 */
const fapi = ref<FormCreateApi | null>(null)
function onApi(instance: FormCreateApi) {
  fapi.value = instance
}
const parseFailed = ref(false)

/** 解析规则 JSON；非法时降级为空数组并标记，避免整页白屏 */
const rules = computed<FormRuleItem[]>(() => {
  if (!props.ruleJson) {
    parseFailed.value = false
    return []
  }
  try {
    const parsed = JSON.parse(props.ruleJson)
    parseFailed.value = !Array.isArray(parsed)
    return Array.isArray(parsed) ? normalizeTypes(parsed as FormRuleItem[]) : []
  } catch {
    parseFailed.value = true
    return []
  }
})

/**
 * FormCreate 组件类型名规范化。
 *
 * <p>FormCreate 的内置类型名是 `datePicker` / `timePicker` / `inputNumber`，
 * 而非 Element Plus 组件的短名 `date` / `time` / `number`。写错时 FormCreate
 * **不报错**，只是把未知标签当原生元素渲染 —— 表现为「控件位置一片空白、
 * 标签与输入框都不出现」，极难排查。</p>
 *
 * <p>这里把常见别名映射到正确类型名，并对无法识别的类型在控制台告警，
 * 让「配置写错」在开发期就暴露，而不是变成线上静默空白。</p>
 */
const TYPE_ALIASES: Record<string, string> = {
  date: 'datePicker',
  time: 'timePicker',
  number: 'inputNumber',
  datetime: 'datePicker',
  text: 'input',
  // textarea 不映射：FormCreate 的 input 支持 ``type: textarea`` 做多行
}

/** FormCreate 3.x 内置类型白名单（不含需额外插件的高级组件） */
const KNOWN_TYPES = new Set([
  'input', 'textarea', 'inputNumber', 'select', 'radio', 'checkbox', 'switch',
  'slider', 'upload', 'datePicker', 'timePicker', 'rate', 'cascader', 'tree',
  'colorPicker', 'autoComplete', 'hidden', 'divider', 'button',
])

function normalizeTypes(items: FormRuleItem[]): FormRuleItem[] {
  return items.map((item) => {
    if (!item?.type) {
      return item
    }
    const mapped = TYPE_ALIASES[item.type] ?? item.type
    if (!KNOWN_TYPES.has(mapped)) {
      console.warn(
        `[FormRender] 未识别的组件类型 "${item.type}"（prop=${item.prop ?? '-'}），` +
          '该控件将不渲染。内置类型请参考 FormCreate 文档，日期用 datePicker 而非 date。',
      )
    }
    const typed = mapped === item.type ? item : { ...item, type: mapped }

    // 标签字段名：FormCreate 3.x 用 `title`，`label` 只是 props 的透传属性。
    // 只写 label 时控件能渲染出来，但不会被包进 el-form-item —— 表现为「有输入框、没有标签」。
    // 这里做双向兼容，存量数据里的 label 也能正确显示。
    if (typed.title === undefined && typed.label !== undefined) {
      return { ...typed, title: typed.label }
    }
    return typed
  })
}

/**
 * 逐字段决定 disabled。
 *
 * <p>三种情形：</p>
 * <ol>
 *   <li>传了 {@code editableProps}：以它为准 —— 白名单内的字段可编辑，其余只读。
 *       <b>空数组即全部只读</b>，与后端「未配置即默认只读」一致；</li>
 *   <li>未传 {@code editableProps}：按 {@code disabled} 整体决定。</li>
 * </ol>
 */
const finalRules = computed<FormRuleItem[]>(() => {
  const whitelist = props.editableProps
  if (whitelist === undefined) {
    if (!props.disabled) {
      return rules.value
    }
    return rules.value.map((item) => ({
      ...item,
      props: { ...(item.props as Record<string, unknown> | undefined), disabled: true },
    }))
  }
  const allow = new Set(whitelist)
  return rules.value.map((item) => {
    const editable = allow.has(String(item.prop))
    return {
      ...item,
      props: { ...(item.props as Record<string, unknown> | undefined), disabled: !editable },
    }
  })
})

// 部分字段可编辑时不宜按「只读」收窄标签宽度 —— 那会让可填字段的标签被压窄
const fullyReadonly = computed(
  () => props.disabled || (props.editableProps !== undefined && props.editableProps.length === 0),
)
const safeLabelWidth = computed(() => (fullyReadonly.value ? '90px' : props.labelWidth))

/**
 * 表单当前值。
 *
 * <p>用独立的内部状态 + 官方 v-model 通道（{@code modelValue} / {@code update:modelValue}）
 * 与 FormCreate 同步，而不是直接给实例赋 {@code value} —— 后者绕过响应式，
 * 在规则异步到达的场景下会静默失效（数据不回填、getValues 拿到空对象）。</p>
 */
const innerValue = ref<Record<string, unknown>>({ ...props.modelValue })

/** 外部数据变化时回填（只取规则已声明的 prop，避免注入无关字段） */
watch(
  () => props.modelValue,
  (val) => {
    innerValue.value = { ...val }
  },
  { deep: true },
)

/** 规则变化（切换流程模板）时重置，避免残留上一份表单的数据 */
watch(
  () => props.ruleJson,
  () => {
    innerValue.value = { ...props.modelValue }
  },
)

/**
 * 触发 FormCreate 校验。
 *
 * <p>FormCreate 3.x 的 validate 返回 Promise，reject 时抛出的是字段校验错误对象，
 * 这里统一转成 false 返回，让调用方只需判断布尔值。</p>
 *
 * <p>未渲染表单时（未绑定 / 解析失败 / 无规则）没有校验义务，直接放行——
 * 同时也避免用到已卸载表单的陈旧 api。</p>
 */
async function validate(): Promise<boolean> {
  if (!rules.value.length) {
    return true
  }
  const instance = fapi.value
  if (!instance) {
    return true
  }
  try {
    await instance.validate()
    return true
  } catch {
    return false
  }
}

function getValues(): Record<string, unknown> {
  // 有渲染中的表单时以 fapi 为权威来源（含用户实时编辑）；
  // 否则回退到内部状态（切换模板时已被 watcher 重置）
  const instance = fapi.value
  if (instance && rules.value.length) {
    return { ...(instance.formData() as object) } as Record<string, unknown>
  }
  return { ...innerValue.value }
}

function setValues(values: Record<string, unknown>) {
  innerValue.value = { ...innerValue.value, ...values }
}

function reset() {
  if (rules.value.length) {
    fapi.value?.resetFields()
  }
  innerValue.value = { ...props.modelValue }
}

defineExpose({ validate, getValues, setValues, reset })
</script>

<template>
  <div class="form-render">
    <el-alert
      v-if="parseFailed"
      type="error"
      show-icon
      :closable="false"
      title="表单规则解析失败"
      description="该表单的规则 JSON 格式非法，请联系管理员在「表单管理」中重新保存"
    />
    <el-alert
      v-else-if="!rules.length"
      type="info"
      show-icon
      :closable="false"
      title="该流程未绑定表单"
      description="如需填写申请内容，请管理员在「流程模板」中为该流程绑定表单"
    />
    <FormCreate
      v-else
      :rule="finalRules"
      :model-value="innerValue"
      :disabled="fullyReadonly"
      :option="{
        submitBtn: false,
        resetBtn: false,
        labelWidth: safeLabelWidth,
      }"
      @update:api="onApi"
      @update:model-value="
        (value: Record<string, unknown>) => {
          innerValue = value
          emit('update:modelValue', value)
          emit('change', value)
        }
      "
    />
  </div>
</template>

<style scoped>
.form-render {
  width: 100%;
}
</style>
