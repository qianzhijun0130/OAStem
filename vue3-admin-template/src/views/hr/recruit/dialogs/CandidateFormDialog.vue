<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  findDuplicateApi,
  RECRUIT_GENDER_OPTIONS,
  RECRUIT_SOURCE_OPTIONS,
  RECRUIT_YESNO_OPTIONS,
  saveCandidateApi,
} from '@/api/recruit'
import type { RecruitCandidateSaveRequest, RecruitCandidateVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 新增 / 编辑候选人弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>手机号查重是「录入前的辅助判断」，不阻断提交（可能确实是同名不同人），
 * 因此放在表单里、跟着手机号走，而不是提交路径上。</p>
 *
 * <p>`authorized`（已获授权）是必填项：未授权简历不应入库，
 * 这是隐私合规的前置门槛，后端也有校验。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<RecruitCandidateSaveRequest>({
  name: '',
  mobile: '',
  email: '',
  gender: undefined,
  birthDate: '',
  postId: undefined,
  demandId: undefined,
  source: 5,
  referrerId: undefined,
  expectSalary: undefined,
  currentSalary: undefined,
  education: '',
  experience: '',
  authorized: 1,
  talentPool: 0,
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  authorized: [{ required: true, message: '请确认是否已获授权', trigger: 'change' }],
}

function resetForm() {
  Object.assign(form, {
    id: null,
    name: '',
    mobile: '',
    email: '',
    gender: undefined,
    birthDate: '',
    postId: undefined,
    demandId: undefined,
    source: 5,
    referrerId: undefined,
    expectSalary: undefined,
    currentSalary: undefined,
    education: '',
    experience: '',
    authorized: 1,
    talentPool: 0,
    remark: '',
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  visible.value = true
}

function openEdit(row: RecruitCandidateVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    name: row.name,
    mobile: row.mobile || '',
    email: row.email || '',
    gender: row.gender,
    birthDate: row.birthDate || '',
    postId: row.postId,
    demandId: row.demandId,
    source: row.source || 5,
    referrerId: row.referrerId,
    expectSalary: row.expectSalary,
    currentSalary: row.currentSalary,
    education: row.education || '',
    experience: row.experience || '',
    authorized: row.authorized ?? 1,
    talentPool: row.talentPool ?? 0,
    remark: row.remark || '',
  })
  visible.value = true
}

async function checkDuplicate() {
  if (!form.mobile) {
    ElMessage.warning('请先填写手机号')
    return
  }
  try {
    const dup = await findDuplicateApi(form.mobile, form.name || undefined)
    if (dup && dup.id) {
      ElMessage.info(`库中已存在该候选人：${dup.name}（阶段 ${dup.stageLabel ?? dup.stage}）`)
    } else {
      ElMessage.success('未发现重复候选人')
    }
  } catch (error) {
    logAsyncError('findDuplicate', error)
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await saveCandidateApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveCandidate', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" :title="editingId ? '编辑候选人' : '新增候选人'" width="620px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="姓名" prop="name">
        <el-input v-model="form.name" maxlength="50" />
      </el-form-item>
      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="form.mobile" maxlength="11" />
      </el-form-item>
      <el-form-item label="查重">
        <el-button data-action="check-dup" @click="checkDuplicate">查重</el-button>
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" maxlength="100" />
      </el-form-item>
      <el-form-item label="性别">
        <el-select v-model="form.gender" clearable placeholder="请选择" style="width: 100%">
          <el-option v-for="o in RECRUIT_GENDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="出生日期">
        <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
      </el-form-item>
      <el-form-item label="应聘职位">
        <el-select v-model="form.postId" clearable filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="p in props.options.state.postOptions" :key="p.id" :label="p.postName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联需求">
        <el-select v-model="form.demandId" clearable filterable placeholder="可不关联" style="width: 100%">
          <el-option
            v-for="d in props.options.state.demandOptions"
            :key="d.id"
            :label="`${d.demandCode} ${d.positionName}`"
            :value="d.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="form.source" style="width: 100%">
          <el-option v-for="o in RECRUIT_SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="内推人">
        <el-select v-model="form.referrerId" clearable filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="u in props.options.state.userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="期望薪资">
        <el-input-number v-model="form.expectSalary" :min="0" :max="9999999" controls-position="right" />
      </el-form-item>
      <el-form-item label="当前薪资">
        <el-input-number v-model="form.currentSalary" :min="0" :max="9999999" controls-position="right" />
      </el-form-item>
      <el-form-item label="学历">
        <el-input v-model="form.education" maxlength="50" />
      </el-form-item>
      <el-form-item label="经验">
        <el-input v-model="form.experience" maxlength="50" />
      </el-form-item>
      <el-form-item label="已授权" prop="authorized">
        <el-radio-group v-model="form.authorized">
          <el-radio v-for="o in RECRUIT_YESNO_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="进人才库">
        <el-radio-group v-model="form.talentPool">
          <el-radio v-for="o in RECRUIT_YESNO_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
        </el-radio-group>
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
