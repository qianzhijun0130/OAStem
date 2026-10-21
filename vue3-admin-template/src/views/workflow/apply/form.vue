<script setup lang="ts">
/**
 * 发起流程 · 填单页
 *
 * <p><b>不进菜单</b>：本页走 `router/index.ts` 的静态隐藏路由 `/workflow/apply/:flowCode`，
 * 只能从「发起流程」列表点卡片进入；左上角返回按钮回到列表。</p>
 *
 * <p><b>三个按钮的语义</b>：
 * <ul>
 *   <li>提交：直接发起（走 `/approval/start`），流程立刻进入审批；</li>
 *   <li>保存：存为草稿（status=1，不启动引擎、不产生待办），下次进来可继续编辑再提交；</li>
 *   <li>关闭：放弃编辑返回列表，不落库。</li>
 * </ul>
 * 草稿重复保存走「更新」而非「新增」，避免连点保存生成多张废草稿。</p>
 *
 * <p>单据编号由后端生成（流程编码-日期-序号），保存后回填展示；申请日期取当天。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFlowOptionsApi } from '@/api/flow'
import { getPrimaryFormApi } from '@/api/form'
import {
  getApprovalDetailApi,
  saveDraftApi,
  startApprovalApi,
  submitDraftApi,
  updateDraftApi,
} from '@/api/approval'
import { logAsyncError } from '@/utils/asyncGuard'
import { useUserStore } from '@/stores/user'
import FormRender from '@/components/form/FormRender.vue'
import AppIcon from '@/components/icons/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const flowCode = computed(() => String(route.params.flowCode ?? ''))
const flowName = ref('')
const ruleJson = ref('')
const title = ref('')
const formValues = ref<Record<string, unknown>>({})
/** 草稿 id：有值说明本次是「编辑草稿」，保存走更新 */
const draftId = ref<number | null>(null)
const billCode = ref('')

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const formRef = ref<InstanceType<typeof FormRender> | null>(null)

/** 申请日期：进入页面当天，只读展示 */
const today = computed(() => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

const applicant = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '-',
)

function back() {
  void router.push('/workflow/apply')
}

/** 载入流程名称 + 主表单规则；若带 draftId 则回填标题与表单内容 */
async function load() {
  loading.value = true
  try {
    const [options, primary] = await Promise.all([
      getFlowOptionsApi(),
      getPrimaryFormApi(flowCode.value).catch(() => null),
      // 未绑定表单是合法状态，降级为不渲染表单区
    ])
    flowName.value = options.find((o) => o.flowCode === flowCode.value)?.flowName ?? ''
    ruleJson.value = primary?.ruleJson ?? ''

    const queryDraft = Number(route.query.draftId)
    if (Number.isFinite(queryDraft) && queryDraft > 0) {
      const detail = await getApprovalDetailApi(queryDraft)
      draftId.value = queryDraft
      billCode.value = detail.instance.billCode ?? ''
      title.value = detail.instance.title ?? ''
      try {
        formValues.value = JSON.parse(detail.instance.formData || '{}')
      } catch {
        // 历史数据可能不是合法 JSON：保持空表单，避免整页崩掉
        formValues.value = {}
      }
    }
  } catch (error) {
    logAsyncError('加载填单页失败', error)
  } finally {
    loading.value = false
  }
}

/** 保存后回填单据编号（后端只在返回 id，编号需再取一次详情） */
async function refreshBillCode(id: number) {
  try {
    const detail = await getApprovalDetailApi(id)
    billCode.value = detail.instance.billCode ?? ''
  } catch (error) {
    logAsyncError('获取单据编号失败', error)
  }
}

function payload() {
  return {
    flowCode: flowCode.value,
    title: title.value.trim(),
    formData: JSON.stringify(formRef.value?.getValues() ?? formValues.value ?? {}),
  }
}

async function save() {
  if (!title.value.trim()) {
    ElMessage.warning('请填写申请标题')
    return
  }
  saving.value = true
  try {
    if (draftId.value) {
      await updateDraftApi(draftId.value, payload())
      await refreshBillCode(draftId.value)
    } else {
      const id = await saveDraftApi(payload())
      draftId.value = id
      await refreshBillCode(id)
    }
    ElMessage.success('草稿已保存，可在「我的流程」继续提交')
  } catch (error) {
    logAsyncError('保存草稿失败', error)
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (!title.value.trim()) {
    ElMessage.warning('请填写申请标题')
    return
  }
  if (formRef.value && !(await formRef.value.validate())) {
    ElMessage.warning('请完善表单必填项')
    return
  }
  submitting.value = true
  try {
    if (draftId.value) {
      // 先把最新编辑写回草稿，再提交，避免提交的是旧内容
      await updateDraftApi(draftId.value, payload())
      await submitDraftApi(draftId.value)
    } else {
      await startApprovalApi(payload())
    }
    ElMessage.success('申请已提交')
    back()
  } catch (error) {
    logAsyncError('提交申请失败', error)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="fill" v-loading="loading">
    <div class="fill__bar">
      <el-button link type="primary" @click="back">
        <AppIcon name="ArrowLeft" :size="16" class="fill__back-icon" />返回
      </el-button>
      <span class="fill__name">{{ flowName || flowCode }}</span>
    </div>

    <div class="fill__card">
      <el-descriptions :column="2" border size="small" class="fill__meta">
        <el-descriptions-item label="单据编号">
          {{ billCode || '保存后自动生成' }}
        </el-descriptions-item>
        <el-descriptions-item label="申请日期">{{ today }}</el-descriptions-item>
        <el-descriptions-item label="流程名称">{{ flowName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ applicant }}</el-descriptions-item>
      </el-descriptions>

      <el-form label-width="110px" class="fill__form">
        <el-form-item label="申请标题" required>
          <el-input v-model="title" placeholder="如：张三 2026-10-01 请假申请" maxlength="100" />
        </el-form-item>

        <FormRender ref="formRef" v-model="formValues" :rule-json="ruleJson" label-width="110px" />

        <el-form-item>
          <el-button
            v-permission="'system:approval:start'"
            type="primary"
            :loading="submitting"
            @click="submit"
          >
            提交
          </el-button>
          <el-button :loading="saving" @click="save">保存</el-button>
          <el-button @click="back">关闭</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.fill {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fill__bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fill__back-icon {
  margin-right: 4px;
}

.fill__name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.fill__card {
  padding: 18px 20px;
  background: #fff;
  border-radius: 6px;
}

.fill__meta {
  margin-bottom: 18px;
}

.fill__form {
  max-width: 760px;
}
</style>
