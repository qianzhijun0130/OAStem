<script setup lang="ts">
/**
 * 表单设计器 / 编辑页。
 *
 * <p>用 FormCreate 官方的 {@code <fc-designer>} 组件做可视化拖拽设计，
 * 产出规则 JSON 后保存到后端 {@code oa_form.rule_json}。</p>
 *
 * <p>两种来源都由此页覆盖：
 * <ul>
 *   <li><b>自定义</b>：从空白开始拖拽生成规则；</li>
 *   <li><b>复用已有表单</b>：顶部下拉选一张已保存的表单，一键载入其规则到设计器，
 *       在其基础上改后另存为新表单（避免直接改动被流程绑定的表单）。</li>
 * </ul>
 *
 * <p>设计器与「编辑」共用本页：带路由 {@code ?id=} 进编辑态（载入并覆盖保存），
 * 不带 id 进新建态。</p>
 */
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFormDetailApi, getFormOptionsApi, saveFormApi } from '@/api/form'
import { logAsyncError } from '@/utils/asyncGuard'
import { useSaveState } from '@/composables/useSaveState'
import SaveStatus from '@/components/SaveStatus.vue'
import type { FormVO, FormRuleItem } from '@/api/types'

/** fc-designer 实例的最小类型声明：只用到本项目实际调用的两个方法 */
interface DesignerInstance {
  getRule: () => FormRuleItem[]
  setRule: (rule: FormRuleItem[]) => void
}

const route = useRoute()
const router = useRouter()

const editId = computed(() => (route.query.id ? Number(route.query.id) : null))
const isEdit = computed(() => editId.value !== null)

const designerRef = ref<DesignerInstance | null>(null)

/**
 * 设计器插件是否已安装。
 *
 * <p>{@code @form-create/designer} 约 4.8M，只有本页面用得到，因此不在 main.ts 全局注册。
 * 就绪前不渲染 {@code <fc-designer>}，否则会渲染成未知元素、留下一片空白。</p>
 *
 * <p><b>注意</b>：该包的默认导出是<b>组件对象</b>，插件安装器是独立的命名导出 {@code install}。
 * 直接 {@code app.use(mod.default)} 拿不到 install，Vue 会静默忽略、组件解析失败（表现为白屏且不报错）。</p>
 */
/**
 * 设计器组件按需加载。
 *
 * <p>{@code @form-create/designer} 约 4.8M，只有本页面用得到，因此不在 main.ts 全局注册——
 * 全局注册会把它塞进 entry，让所有页面为首屏多下 2MB（实测 entry 2039kB → 1091kB）。</p>
 *
 * <p><b>为什么用 defineAsyncComponent 而不是 app.use</b>：该包的默认导出是<b>组件对象</b>，
 * 插件安装器是独立的命名导出 {@code install}，而 {@code app.use({install})} 实测并未注册出
 * {@code <fc-designer>}（控制台报 Failed to resolve component）。直接异步渲染默认导出最稳。</p>
 */
const FcDesignerComp = defineAsyncComponent(async () => {
  const mod = await import('@form-create/designer') as unknown as {
    default?: unknown
    designerForm?: unknown
  }
  return (mod.default ?? mod.designerForm) as never
})
const formOptions = ref<FormVO[]>([])

/** 保存状态：保存后不跳转、不重载，仅更新状态指示（详见 useSaveState） */
const saver = useSaveState()
const saving = saver.busy

/**
 * 最近一次保存成功返回的表单 id。
 *
 * <p>新建时后端返回新 id，用它把路由切到编辑态（{@code router.replace} 不销毁组件，
 * 设计内容与已填字段全部保留），避免用户「保存 → 再保存」时重复新增两条记录。</p>
 */
const lastSavedId = ref<number | null>(null)

/** 元信息与规则分开：设计器只管规则，编码/名称/分类在表单区填 */
const meta = reactive({
  formCode: '',
  formName: '',
  category: '',
  remark: '',
  status: 1,
})

/** 复用已有表单：选中的编码，change 时载入设计器 */
const reuseCode = ref<string>('')

/** 表单校验规则（与后端 DTO 的约束对齐，前端先拦一道） */
const rules = {
  formCode: [
    { required: true, message: '表单编码不能为空', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_-]{1,63}$/,
      message: '须以字母开头，只能含字母、数字、下划线、短横',
      trigger: 'blur',
    },
  ],
  formName: [{ required: true, message: '表单名称不能为空', trigger: 'blur' }],
}

const formRef = ref()

/** 载入已有表单的规则到设计器 */
function onReuseChange(code: string) {
  if (!code) {
    return
  }
  const target = formOptions.value.find((f) => f.formCode === code)
  if (!target?.ruleJson) {
    ElMessage.warning('该表单没有规则内容')
    return
  }
  try {
    designerRef.value?.setRule(JSON.parse(target.ruleJson) as FormRuleItem[])
    // 编码/名称加「_副本」后缀，避免与来源表单撞唯一索引
    meta.formCode = `${target.formCode}_copy`
    meta.formName = `${target.formName} 副本`
    ElMessage.success(`已载入「${target.formName}」的规则，保存后会存为新表单`)
  } catch (error) {
    logAsyncError('载入表单规则失败', error)
    ElMessage.error('该表单的规则 JSON 无法解析')
  }
}

async function loadOptions() {
  try {
    formOptions.value = await getFormOptionsApi()
  } catch (error) {
    logAsyncError('加载表单选项失败', error)
  }
}

async function loadDetail() {
  if (!editId.value) {
    return
  }
  try {
    const detail = await getFormDetailApi(editId.value)
    meta.formCode = detail.formCode
    meta.formName = detail.formName
    meta.category = detail.category ?? ''
    meta.remark = detail.remark ?? ''
    meta.status = detail.status
    if (detail.ruleJson) {
      designerRef.value?.setRule(JSON.parse(detail.ruleJson) as FormRuleItem[])
    }
  } catch (error) {
    logAsyncError('加载表单详情失败', error)
  }
}

async function save() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const rule = designerRef.value?.getRule() ?? []
  if (!Array.isArray(rule) || rule.length === 0) {
    ElMessage.warning('请先在设计器中拖拽添加表单控件')
    return
  }

  const ok = await saver.run(
    async () => {
      const newId = await saveFormApi({
        id: editId.value ?? undefined,
        formCode: meta.formCode.trim(),
        formName: meta.formName.trim(),
        category: meta.category.trim() || undefined,
        ruleJson: JSON.stringify(rule),
        remark: meta.remark.trim() || undefined,
        status: meta.status,
      })
      if (typeof newId === 'number' && newId > 0) {
        lastSavedId.value = newId
      }
      return newId
    },
    {
      label: '保存表单',
      // 设计器的规则已完整保存在本地组件内，保存后无需回读任何数据；
      // 「返回列表」交由用户主动点击，避免销毁组件丢掉全部编辑进度。
      onSuccess: null,
    },
  )
  if (ok) {
    ElMessage.success(isEdit.value ? '已保存，返回列表后即可看到最新数据' : '已创建')
    // 新建成功后把路由切到编辑态：后续再点保存走更新而非重复新增，
    // 同时不动组件、不丢任何设计内容。
    if (!isEdit.value) {
      saver.reset()
      router.replace({ path: '/system/form/designer', query: { id: String(lastSavedId.value) } })
    }
  }
}

async function goBack() {
  try {
    await ElMessageBox.confirm('确认离开？未保存的设计内容将丢失。', '提示', {
      type: 'warning',
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
    })
  } catch {
    return
  }
  router.push('/system/form')
}

onMounted(() => {
  loadOptions()
  loadDetail()
})
</script>

<template>
  <div class="form-designer">
    <el-card shadow="never">
      <div class="form-designer__head">
        <div>
          <span class="form-designer__title">{{ isEdit ? '编辑表单' : '新增表单' }}</span>
          <el-tag v-if="isEdit" type="info" size="small" style="margin-left: 8px">
            编辑中：保存将直接覆盖原表单
          </el-tag>
        </div>
        <div class="form-designer__actions">
          <!-- 轻量状态告知：Toast 会自动消失，这里常驻可随时回看保存时间 -->
          <SaveStatus :saved-at="saver.savedAt.value" :dirty="saver.dirty.value" />
          <el-button @click="goBack">返回列表</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>

      <el-form ref="formRef" :model="meta" :rules="rules" label-width="100px" class="form-designer__meta">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="表单编码" prop="formCode">
              <el-input
                v-model="meta.formCode"
                placeholder="如 leave_apply"
                :disabled="isEdit"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="表单名称" prop="formName">
              <el-input v-model="meta.formName" placeholder="如 请假申请表单" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分类">
              <el-input v-model="meta.category" placeholder="如 内置表单" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="状态">
              <el-radio-group v-model="meta.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注">
              <el-input v-model="meta.remark" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="复用已有">
          <el-select
            v-model="reuseCode"
            placeholder="载入一张已保存表单的规则作为起点"
            clearable
            filterable
            style="width: 360px"
            @change="onReuseChange"
          >
            <el-option
              v-for="f in formOptions"
              :key="f.formCode"
              :label="`${f.formName}（${f.formCode}）`"
              :value="f.formCode"
            />
          </el-select>
          <span class="form-designer__tip">
            载入后会在此基础上设计并另存为新表单，不会改动来源表单
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="form-designer__canvas">
      <!-- FormCreate 官方设计器：左侧组件面板拖拽，右侧配置，底部实时预览 -->
      <component :is="FcDesignerComp" ref="designerRef" height="calc(100vh - 400px)" />
    </el-card>
  </div>
</template>

<style scoped>
.form-designer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.form-designer__title {
  font-size: 16px;
  font-weight: 600;
}

.form-designer__meta {
  margin-bottom: 4px;
}

.form-designer__tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

.form-designer__canvas {
  margin-top: 12px;
}
</style>
