<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { RECRUIT_CHANNEL_OPTIONS, RECRUIT_POST_STATUS_OPTIONS, savePostApi } from '@/api/recruit'
import type { RecruitPostSaveRequest, RecruitPostVO } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 新增 / 编辑招聘职位弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>可关联需求（可不关联）；关联需求下拉来自共享 options，新建需求后立刻可选。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<RecruitPostSaveRequest>({
  demandId: undefined,
  postName: '',
  city: '',
  salaryMin: undefined,
  salaryMax: undefined,
  headCount: 1,
  channel: 1,
  status: 1,
  hrId: undefined,
  remark: '',
})

const rules: FormRules = {
  postName: [{ required: true, message: '请输入职位名称', trigger: 'blur' }],
  headCount: [{ required: true, message: '请输入招聘人数', trigger: 'blur' }],
}

function resetForm() {
  Object.assign(form, {
    id: null,
    demandId: undefined,
    postName: '',
    city: '',
    salaryMin: undefined,
    salaryMax: undefined,
    headCount: 1,
    channel: 1,
    status: 1,
    hrId: undefined,
    remark: '',
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  visible.value = true
}

function openEdit(row: RecruitPostVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    demandId: row.demandId,
    postName: row.postName,
    city: row.city || '',
    salaryMin: row.salaryMin,
    salaryMax: row.salaryMax,
    headCount: row.headCount || 1,
    channel: row.channel || 1,
    status: row.status || 1,
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
    await savePostApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('savePost', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" :title="editingId ? '编辑职位' : '新增职位'" width="560px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
      <el-form-item label="职位名称" prop="postName">
        <el-input v-model="form.postName" maxlength="100" />
      </el-form-item>
      <el-form-item label="工作地点">
        <el-input v-model="form.city" maxlength="50" />
      </el-form-item>
      <el-form-item label="薪资范围">
        <el-input-number v-model="form.salaryMin" :min="0" :max="9999999" controls-position="right" /> ~
        <el-input-number v-model="form.salaryMax" :min="0" :max="9999999" controls-position="right" />
      </el-form-item>
      <el-form-item label="招聘人数" prop="headCount">
        <el-input-number v-model="form.headCount" :min="1" :max="999" controls-position="right" />
      </el-form-item>
      <el-form-item label="渠道">
        <el-select v-model="form.channel" style="width: 100%">
          <el-option v-for="o in RECRUIT_CHANNEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="o in RECRUIT_POST_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
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
