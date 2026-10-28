<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getLeavePolicyTypesApi, saveLeavePolicyApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { LeavePolicySaveRequest, LeavePolicyVO } from '@/api/types'

/**
 * 新增 / 编辑假别政策弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>假别下拉的选项来自后端 `policy/types`（与写死的 `LEAVE_TYPES` 不同，
 * 政策只对「有法定天数」的假别开放）。选项在本组件内按需加载并缓存，
 * 切 tab 不会再重复请求。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const types = ref<{ code: number; label: string }[]>([])
const form = reactive<LeavePolicySaveRequest>({
  city: '',
  leaveType: 4,
  days: 3,
  effectiveYear: new Date().getFullYear(),
  remark: '',
})

const rules: FormRules = {
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  leaveType: [{ required: true, message: '请选择假别', trigger: 'change' }],
  effectiveYear: [{ required: true, message: '请输入生效年度', trigger: 'change' }],
}

async function loadTypes() {
  if (types.value.length > 0) {
    return
  }
  try {
    types.value = (await getLeavePolicyTypesApi()) || []
  } catch (error) {
    logAsyncError('loadLeavePolicyTypes', error)
  }
}

/** 新增：城市与年度默认跟随当前筛选条件，减少一次手填 */
function openCreate(defaults: { city?: string; year?: string }) {
  editingId.value = null
  Object.assign(form, {
    city: defaults.city || '',
    leaveType: types.value[0]?.code ?? 4,
    days: 3,
    effectiveYear: defaults.year ? Number(defaults.year) : new Date().getFullYear(),
    remark: '',
  })
  void loadTypes()
  visible.value = true
}

function openEdit(row: LeavePolicyVO) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    city: row.city,
    leaveType: row.leaveType,
    days: row.days,
    effectiveYear: row.effectiveYear,
    remark: row.remark || '',
  })
  void loadTypes()
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await saveLeavePolicyApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveLeavePolicy', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <ResponsiveDialog
    v-model="visible"
    :title="editingId ? '编辑假别政策' : '新增假别政策'"
    width="520px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="城市" prop="city">
        <el-input v-model="form.city" maxlength="50" placeholder="如 上海" />
      </el-form-item>
      <el-form-item label="假别" prop="leaveType">
        <el-select v-model="form.leaveType" style="width: 100%">
          <el-option v-for="t in types" :key="t.code" :label="t.label" :value="t.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="法定天数" prop="days">
        <el-input-number v-model="form.days" :min="0.5" :step="0.5" :precision="1" />
        <span class="hr-leave__hint">天</span>
      </el-form-item>
      <el-form-item label="生效年度" prop="effectiveYear">
        <el-input-number v-model="form.effectiveYear" :min="2000" :max="2100" :controls="false" style="width: 140px" />
      </el-form-item>
      <el-form-item label="依据">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="选填，如《女职工劳动保护特别规定》"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </ResponsiveDialog>
</template>
