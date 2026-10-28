<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { RECRUIT_REASON_TYPE_OPTIONS, RECRUIT_URGENCY_OPTIONS, saveDemandApi } from '@/api/recruit'
import type { RecruitDemandSaveRequest, RecruitDemandVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 新增 / 编辑招聘需求弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>部门走树选择（`el-tree-select`），岗位与负责 HR 走下拉；三份选项都来自
 * 页面共享的 `options`，不在弹窗里各自拉取。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<RecruitDemandSaveRequest>({
  deptId: undefined,
  positionId: undefined,
  headCount: 1,
  urgency: 1,
  expectDate: '',
  education: '',
  experience: '',
  skillRequire: '',
  salaryMin: undefined,
  salaryMax: undefined,
  reasonType: 1,
  reason: '',
  hrId: undefined,
  remark: '',
})

const rules: FormRules = {
  deptId: [{ required: true, message: '请选择需求部门', trigger: 'change' }],
  positionId: [{ required: true, message: '请选择需求岗位', trigger: 'change' }],
  headCount: [{ required: true, message: '请输入招聘人数', trigger: 'blur' }],
}

function resetForm() {
  Object.assign(form, {
    id: null,
    deptId: undefined,
    positionId: undefined,
    headCount: 1,
    urgency: 1,
    expectDate: '',
    education: '',
    experience: '',
    skillRequire: '',
    salaryMin: undefined,
    salaryMax: undefined,
    reasonType: 1,
    reason: '',
    hrId: undefined,
    remark: '',
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  visible.value = true
}

function openEdit(row: RecruitDemandVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    deptId: row.deptId,
    positionId: row.positionId,
    headCount: row.headCount || 1,
    urgency: row.urgency || 1,
    expectDate: row.expectDate || '',
    education: row.education || '',
    experience: row.experience || '',
    skillRequire: row.skillRequire || '',
    salaryMin: row.salaryMin,
    salaryMax: row.salaryMax,
    reasonType: row.reasonType || 1,
    reason: row.reason || '',
    hrId: row.hrId,
    remark: row.remark || '',
  })
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await saveDemandApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveDemand', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" :title="editingId ? '编辑需求' : '新增需求'" width="620px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="需求部门" prop="deptId">
        <el-tree-select
          v-model="form.deptId"
          :data="props.options.state.deptTree"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          check-strictly
          placeholder="请选择部门"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="需求岗位" prop="positionId">
        <el-select v-model="form.positionId" placeholder="请选择岗位" filterable style="width: 100%">
          <el-option
            v-for="p in props.options.state.positionOptions"
            :key="p.id"
            :label="`${p.positionName}（${p.positionCode}）`"
            :value="p.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="招聘人数" prop="headCount">
        <el-input-number v-model="form.headCount" :min="1" :max="999" controls-position="right" />
      </el-form-item>
      <el-form-item label="紧急度">
        <el-select v-model="form.urgency" style="width: 100%">
          <el-option v-for="o in RECRUIT_URGENCY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="期望到岗">
        <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
      </el-form-item>
      <el-form-item label="学历要求">
        <el-input v-model="form.education" placeholder="如 本科及以上" />
      </el-form-item>
      <el-form-item label="经验要求">
        <el-input v-model="form.experience" placeholder="如 3年以上" />
      </el-form-item>
      <el-form-item label="技能要求">
        <el-input v-model="form.skillRequire" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="薪资范围">
        <el-input-number v-model="form.salaryMin" :min="0" :max="9999999" controls-position="right" /> ~
        <el-input-number v-model="form.salaryMax" :min="0" :max="9999999" controls-position="right" />
      </el-form-item>
      <el-form-item label="招聘原因">
        <el-select v-model="form.reasonType" style="width: 100%">
          <el-option v-for="o in RECRUIT_REASON_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="原因说明">
        <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="负责HR">
        <el-select v-model="form.hrId" clearable filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="u in props.options.state.userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-action="submit" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
