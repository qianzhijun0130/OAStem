<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { saveSalaryProfileApi } from '@/api/hr'
import { getUserPageApi } from '@/api/user'
import { logAsyncError } from '@/utils/asyncGuard'
import type { SalaryProfileVO } from '@/api/types'
import { currentMonth } from '../period'

/**
 * 薪资档案 / 调薪弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p><b>表单 ref 必须在本组件内声明</b>：`<el-form ref="formRef">` 只能绑定组件自身作用域，
 * 放进 composable 会变成两份 ref，`formRef.value.validate()` 拿到 undefined 且不报错。
 * 这也是本组件没有把表单状态外置的原因。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const userOptions = ref<{ value: number; label: string }[]>([])
let userOptionsLoaded = false

const form = reactive({
  userId: undefined as number | undefined,
  effectiveDate: currentMonth() + '-01',
  baseSalary: 0,
  postSalary: 0,
  perfSalary: 0,
  allowance: 0,
  socialBase: undefined as number | undefined,
  fundBase: undefined as number | undefined,
  city: '默认',
  specialDeduct: 0,
  changeReason: '',
})

const rules: FormRules = {
  userId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
}

async function loadUserOptions() {
  if (userOptionsLoaded) {
    return
  }
  try {
    // 没有专门的「用户下拉」接口，直接取一页最大 500 条：薪资档案只对在职员工维护，
    // 500 足够覆盖中小规模企业；超出的场景应改为服务端搜索
    const page = await getUserPageApi({ pageNo: 1, pageSize: 500, status: 1 })
    userOptions.value = (page.records || []).map((u) => ({
      value: u.id,
      label: `${u.realName || u.username}（${u.workNumber || ''}）`,
    }))
    userOptionsLoaded = true
  } catch (error) {
    logAsyncError('loadUserOptions', error)
  }
}

/** 调薪默认从「下个月 1 日」生效：当月已经算过的薪资不能被追溯改掉 */
function nextMonthFirstDay() {
  const d = new Date()
  d.setMonth(d.getMonth() + 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

async function open(row?: SalaryProfileVO) {
  await loadUserOptions()
  if (row) {
    form.userId = row.userId
    form.effectiveDate = nextMonthFirstDay()
    form.baseSalary = Number(row.baseSalary || 0)
    form.postSalary = Number(row.postSalary || 0)
    form.perfSalary = Number(row.perfSalary || 0)
    form.allowance = Number(row.allowance || 0)
    form.socialBase = row.socialBase ?? undefined
    form.fundBase = row.fundBase ?? undefined
    form.city = row.city || '默认'
    form.specialDeduct = Number(row.specialDeduct || 0)
    form.changeReason = ''
  } else {
    form.userId = undefined
    form.effectiveDate = currentMonth() + '-01'
    form.baseSalary = 0
    form.postSalary = 0
    form.perfSalary = 0
    form.allowance = 0
    form.socialBase = undefined
    form.fundBase = undefined
    form.city = '默认'
    form.specialDeduct = 0
    form.changeReason = ''
  }
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await saveSalaryProfileApi({
      userId: form.userId as number,
      effectiveDate: form.effectiveDate,
      baseSalary: form.baseSalary,
      postSalary: form.postSalary,
      perfSalary: form.perfSalary,
      allowance: form.allowance,
      socialBase: form.socialBase,
      fundBase: form.fundBase,
      city: form.city,
      specialDeduct: form.specialDeduct,
      changeReason: form.changeReason,
    })
    ElMessage.success('已保存（新增一段档案，历史月份不受影响）')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveSalaryProfile', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="薪资档案 / 调薪" width="640px">
    <el-alert
      class="salary__tip"
      type="info"
      :closable="false"
      show-icon
      title="调薪不是覆盖原记录，而是新增一段更晚生效的档案——历史月份必须能算回原值。"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="员工" prop="userId">
        <el-select v-model="form.userId" filterable placeholder="选择员工" style="width: 100%">
          <el-option v-for="u in userOptions" :key="u.value" :label="u.label" :value="u.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期" prop="effectiveDate">
        <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="基本工资">
            <el-input-number v-model="form.baseSalary" :min="0" :precision="2" :step="500" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="岗位工资">
            <el-input-number v-model="form.postSalary" :min="0" :precision="2" :step="500" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="绩效基数">
            <el-input-number v-model="form.perfSalary" :min="0" :precision="2" :step="500" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="固定津贴">
            <el-input-number v-model="form.allowance" :min="0" :precision="2" :step="100" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="社保基数">
            <el-input-number v-model="form.socialBase" :min="0" :precision="2" :step="100" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="公积金基数">
            <el-input-number v-model="form.fundBase" :min="0" :precision="2" :step="100" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="参保城市">
            <el-input v-model="form.city" placeholder="默认" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="专项附加扣除">
            <el-input-number v-model="form.specialDeduct" :min="0" :precision="2" :step="500" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="调薪原因">
        <el-input v-model="form.changeReason" placeholder="如：年度调薪 / 晋升" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
