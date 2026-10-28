<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  saveSessionApi,
  TRAIN_CHECKIN_WAY_OPTIONS,
  type TrainCourseVO,
  type TrainSessionSaveRequest,
  type TrainSessionVO,
} from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 新增 / 编辑培训期次弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>期次必须挂在课程下，所以课程下拉是必填项；名额 0 表示不限（后端语义）。</p>
 */
const props = defineProps<{
  /** 课程全量选项：由页面统一加载后传入 */
  courseOptions: TrainCourseVO[]
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<TrainSessionSaveRequest>({
  courseId: undefined,
  sessionName: '',
  startTime: '',
  endTime: '',
  location: '',
  onlineLink: '',
  capacity: 0,
  enrollDeadline: '',
  checkinWay: 1,
  remark: '',
})

const rules: FormRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  sessionName: [{ required: true, message: '请输入期次名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

function resetForm() {
  Object.assign(form, {
    id: null,
    courseId: undefined,
    sessionName: '',
    startTime: '',
    endTime: '',
    location: '',
    onlineLink: '',
    capacity: 0,
    enrollDeadline: '',
    checkinWay: 1,
    remark: '',
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  visible.value = true
}

function openEdit(row: TrainSessionVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    courseId: row.courseId,
    sessionName: row.sessionName,
    startTime: row.startTime || '',
    endTime: row.endTime || '',
    location: row.location || '',
    onlineLink: row.onlineLink || '',
    capacity: row.capacity ?? 0,
    enrollDeadline: row.enrollDeadline || '',
    checkinWay: row.checkinWay || 1,
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
    await saveSessionApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveSession', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" :title="editingId ? '编辑期次' : '新增期次'" width="600px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="课程" prop="courseId">
        <el-select v-model="form.courseId" filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="c in props.courseOptions" :key="c.id" :label="c.courseName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="期次名称" prop="sessionName">
        <el-input v-model="form.sessionName" maxlength="100" placeholder="如 2026春季班" />
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker
          v-model="form.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker
          v-model="form.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="地点">
        <el-input v-model="form.location" maxlength="200" />
      </el-form-item>
      <el-form-item label="线上链接">
        <el-input v-model="form.onlineLink" maxlength="300" />
      </el-form-item>
      <el-form-item label="名额">
        <el-input-number v-model="form.capacity" :min="0" :max="99999" controls-position="right" />
        <span class="training__hint">0 表示不限</span>
      </el-form-item>
      <el-form-item label="报名截止">
        <el-date-picker
          v-model="form.enrollDeadline"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="签到方式">
        <el-select v-model="form.checkinWay" style="width: 100%">
          <el-option v-for="o in TRAIN_CHECKIN_WAY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
