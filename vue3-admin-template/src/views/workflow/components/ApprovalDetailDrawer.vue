<script setup lang="ts">
/**
 * 审批详情抽屉。
 *
 * <p>三个区块自上而下：申请单信息 → <b>申请人填写的表单（只读）</b> → 独立审批区 → 流转记录 → 流程图。</p>
 *
 * <p><b>表单复用</b>：所有审批节点都复用发起时那份 FormCreate 表单，规则由详情接口
 * （{@code formRuleJson}）统一下发，审批人无需再为每个节点配表单。</p>
 *
 * <p><b>审批区独立</b>：审批意见与同意/驳回不混在表单里，避免审批人误改申请人填的内容。</p>
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import BpmnViewer from '@/components/bpmn/BpmnViewer.vue'
import FormRender from '@/components/form/FormRender.vue'
import {
  ccInstanceApi,
  completeApprovalApi,
  getApprovalDetailApi,
  getCcUsersApi,
} from '@/api/approval'
import { getFlowBpmnApi, getFlowPageApi } from '@/api/flow'
import { logAsyncError } from '@/utils/asyncGuard'
import type { ApprovalDetailVO, CcUserVO } from '@/api/types'

const props = defineProps<{ visible: boolean; instanceId?: number }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'done'): void }>()

const loading = ref(false)
const detail = ref<ApprovalDetailVO | null>(null)
const bpmnXml = ref('')
/** 该审批单所属流程的主表单规则 JSON；空串表示未绑定或无规则 */
const formRuleJson = ref('')
/** 审批单提交的表单数据，交给 FormRender 只读回填 */
const formData = ref<Record<string, unknown>>({})
/** 当前节点允许编辑的字段；空数组表示全部只读 */
const editableProps = ref<string[]>([])
/** 表单实例，用于提交时取当前值 */
const formRef = ref<InstanceType<typeof FormRender> | null>(null)

/** 当前节点是否有可编辑字段 —— 决定表单区是否提示「部分字段可填写」 */
const hasEditable = computed(() => editableProps.value.length > 0)

/* ---------------- 审批区 ---------------- */
const submitting = ref(false)
const opinion = ref('')
/**
 * 是否显示审批区：由后端 {@code canApprove} 决定（当前用户是该节点候选人或超管）。
 *
 * <p>不再用 `currentTask` 是否为空判断——那是「流程停在哪个节点」，申请人 / 实例总览用户也能看到，
 * 据此会给他们显示一个点了必然 3104 的审批区。</p>
 */
const canApprove = computed(() => Boolean(detail.value?.canApprove))

function statusTagType(status?: number): 'success' | 'info' | 'danger' | 'warning' {
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  if (status === 5) return 'info'
  return 'warning'
}

/** 解析提交时存下的 form_data；非法 JSON 按空对象处理，不影响详情其余部分展示 */
function parseFormData(json?: string): Record<string, unknown> {
  if (!json) {
    return {}
  }
  try {
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}

async function load() {
  if (!props.instanceId) {
    return
  }
  loading.value = true
  try {
    detail.value = await getApprovalDetailApi(props.instanceId)
    formData.value = parseFormData(detail.value.instance.formData)
    // 表单规则随详情一并下发（后端取该流程发起环节的主表单）：
    // 无需再单独请求表单接口，审批人天然能看到申请人填的那份表单。
    formRuleJson.value = detail.value.formRuleJson ?? ''
    // 未配置字段权限时后端返回空数组 —— 全部只读，与「默认只读」约定一致
    editableProps.value = detail.value.editableProps ?? []
    opinion.value = ''

    // 流程图仍需模板管理权限：拿不到时静默降级为不展示流程图，不影响详情主体
    const flowCode = detail.value.instance.flowCode
    if (flowCode) {
      const page = await getFlowPageApi({ pageNo: 1, pageSize: 1, flowCode }).catch(() => null)
      const def = page?.records?.[0]
      bpmnXml.value = def ? await getFlowBpmnApi(def.id).catch(() => '') : ''
    }
  } finally {
    loading.value = false
  }
}

/**
 * 提交审批。
 *
 * <p>驳回会终止流程（项目既定语义），故驳回要求必须填意见；同意选填，便于后续追溯。
 * 与待办列表页（todo）的校验契约保持一致。</p>
 */
async function submitApproval(action: 'approve' | 'reject') {
  const taskId = detail.value?.currentTask?.taskId
  if (!taskId) {
    return
  }
  if (action === 'reject' && !opinion.value.trim()) {
    ElMessage.warning('驳回时请填写审批意见')
    return
  }
  submitting.value = true
  try {
    // 当前节点有可编辑字段时，把改动作为补丁一并提交（销假节点填销假信息）。
    // 后端只合并被授权可编辑的字段，这里即便多传也不会被写入。
    const patch = hasEditable.value ? formRef.value?.getValues() ?? {} : undefined
    await completeApprovalApi({
      taskId,
      action,
      opinion: opinion.value.trim(),
      formDataPatch: hasEditable.value ? patch : undefined,
    })
    ElMessage.success(action === 'approve' ? '已同意' : '已驳回')
    opinion.value = ''
    emit('done')
    emit('update:visible', false)
  } catch (error) {
    logAsyncError('提交审批失败', error)
  } finally {
    submitting.value = false
  }
}

/* ---------------- 抄送 ---------------- */
const ccVisible = ref(false)
const ccSubmitting = ref(false)
const ccUsers = ref<CcUserVO[]>([])
const ccUserIds = ref<number[]>([])
const ccRemark = ref('')

/** 打开抄送弹窗：候选用户按需加载（只读接口，不要求用户查询权限） */
async function openCc() {
  ccUserIds.value = []
  ccRemark.value = ''
  ccVisible.value = true
  if (ccUsers.value.length > 0) {
    return
  }
  try {
    ccUsers.value = await getCcUsersApi()
  } catch (error) {
    logAsyncError('加载抄送人候选失败', error)
  }
}

async function submitCc() {
  if (!props.instanceId) {
    return
  }
  if (ccUserIds.value.length === 0) {
    ElMessage.warning('请至少选择一个抄送人')
    return
  }
  ccSubmitting.value = true
  try {
    await ccInstanceApi({
      instanceId: props.instanceId,
      userIds: ccUserIds.value,
      remark: ccRemark.value.trim() || undefined,
    })
    ElMessage.success('已抄送')
    ccVisible.value = false
    // 抄送会写一条流转记录，刷新详情让时间轴立刻看到
    await load()
  } catch (error) {
    logAsyncError('抄送失败', error)
  } finally {
    ccSubmitting.value = false
  }
}

watch(
  () => [props.visible, props.instanceId],
  async ([visible]) => {
    if (visible && props.instanceId) {
      await load()
    } else if (!visible) {
      detail.value = null
      bpmnXml.value = ''
      formRuleJson.value = ''
      formData.value = {}
      editableProps.value = []
      opinion.value = ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer
    :model-value="visible"
    :size="'60%'"
    title="审批详情"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border class="detail__desc">
          <el-descriptions-item label="申请标题">{{ detail.instance.title }}</el-descriptions-item>
          <el-descriptions-item label="流程名称">
            {{ detail.instance.flowName }}
          </el-descriptions-item>
          <el-descriptions-item label="发起人">
            {{ detail.instance.applicantName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.instance.status)">
              {{ detail.instance.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">
            {{ detail.instance.currentNode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="发起时间">
            {{ detail.instance.createTime || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 抄送：告知性动作，任何能看详情的人都可以把单据抄送给他人（权限码控制） -->
        <div class="detail__ops">
          <el-button v-permission="'system:approval:cc'" size="small" @click="openCc">抄送</el-button>
        </div>

        <el-divider content-position="left">申请内容</el-divider>
        <!-- 表单区：有可编辑字段时是「部分可填」（销假场景），否则恒为只读 -->
        <el-alert
          v-if="!hasEditable"
          type="info"
          :closable="false"
          show-icon
          title="以下内容由申请人在发起时填写，审批时仅供查看"
          class="detail__hint"
        />
        <el-alert
          v-else
          type="warning"
          :closable="false"
          show-icon
          title="当前节点需要你补充填写部分信息"
          description="标为可编辑的字段需要填写后再提交，其余字段仍由申请人填写、不可修改。"
          class="detail__hint"
        />
        <FormRender
          v-if="formRuleJson"
          ref="formRef"
          :rule-json="formRuleJson"
          :model-value="formData"
          :editable-props="editableProps"
        />
        <!-- 无规则但有数据时降级为键值对（兼容未绑定表单的历史单据） -->
        <el-descriptions v-else-if="Object.keys(formData).length" :column="1" border>
          <el-descriptions-item
            v-for="(value, key) in formData"
            :key="key"
            :label="String(key)"
          >
            {{ value === null || value === undefined || value === '' ? '-' : value }}
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="无表单数据" :image-size="60" />

        <!-- 审批区：与表单区严格分离，审批人只在这里填意见并决策 -->
        <template v-if="canApprove">
          <el-divider content-position="left">我的审批</el-divider>
          <div class="detail__approval">
            <div class="detail__approval-node">
              当前节点：<strong>{{ detail.currentTask?.currentNode || '-' }}</strong>
            </div>
            <el-input
              v-model="opinion"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请填写审批意见（驳回时必填）"
            />
            <div class="detail__approval-actions">
              <el-button
                v-permission="'system:approval:complete'"
                type="danger"
                plain
                :loading="submitting"
                @click="submitApproval('reject')"
              >
                驳回
              </el-button>
              <el-button
                v-permission="'system:approval:complete'"
                type="primary"
                :loading="submitting"
                @click="submitApproval('approve')"
              >
                同意
              </el-button>
            </div>
          </div>
        </template>

        <el-divider content-position="left">流程图</el-divider>
        <BpmnViewer v-if="bpmnXml" :xml="bpmnXml" />
        <el-empty v-else description="未获取到流程图" :image-size="60" />

        <el-divider content-position="left">流转记录</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="(rec, idx) in detail.records"
            :key="idx"
            :timestamp="rec.createTime || ''"
            :type="rec.action === 'reject' ? 'danger' : rec.action === 'approve' ? 'success' : 'primary'"
            placement="top"
          >
            <div class="detail__record">
              <strong>{{ rec.nodeName || '-' }}</strong>
              <el-tag size="small" class="detail__action">{{ rec.actionName }}</el-tag>
              <span class="detail__handler">{{ rec.handlerName || '-' }}</span>
            </div>
            <div v-if="rec.opinion" class="detail__opinion">意见：{{ rec.opinion }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
      <el-empty v-else-if="!loading" description="暂无详情" />
    </div>

    <!-- 抄送弹窗：选择人员 + 可选说明 -->
    <el-dialog v-model="ccVisible" title="抄送审批单" width="460px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="抄送人" required>
          <el-select
            v-model="ccUserIds"
            multiple
            filterable
            placeholder="请选择抄送人"
            style="width: 100%"
          >
            <el-option
              v-for="u in ccUsers"
              :key="u.id"
              :label="`${u.realName}${u.deptName ? '（' + u.deptName + '）' : ''}`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="ccRemark"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="可选，会记录在流转记录里"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ccVisible = false">取消</el-button>
        <el-button type="primary" :loading="ccSubmitting" @click="submitCc">确定抄送</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.detail__desc {
  margin-bottom: 8px;
}

.detail__ops {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}

.detail__record {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail__action {
  margin-left: 4px;
}

.detail__handler {
  color: var(--el-text-color-secondary);
}

.detail__opinion {
  margin-top: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.detail__hint {
  margin-bottom: 10px;
}

/* 审批区与表单区视觉隔离，避免审批人误以为可以改申请人填的内容 */
.detail__approval {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  background-color: var(--el-fill-color-lighter);
}

.detail__approval-node {
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.detail__approval-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
