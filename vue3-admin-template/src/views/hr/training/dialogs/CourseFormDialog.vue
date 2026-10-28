<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  saveCourseApi,
  TRAIN_COURSE_STATUS_OPTIONS,
  TRAIN_REQUIRED_OPTIONS,
  TRAIN_TEACHER_TYPE_OPTIONS,
  TRAIN_TYPE_OPTIONS,
  type TrainCourseSaveRequest,
  type TrainCourseVO,
} from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'
import type { UserVO } from '@/api/types'

/**
 * 新增 / 编辑培训课程弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>讲师分内部/外部两套字段：内部选人（`teacherId`），外部填姓名与机构。
 * 表单 ref 留在组件内，否则 `validate()` 会静默失效。</p>
 */
const props = defineProps<{
  /** 员工候选：内部讲师下拉；由页面统一加载后传入 */
  userCandidates: UserVO[]
}>()

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<TrainCourseSaveRequest>({
  courseCode: '',
  courseName: '',
  trainType: 1,
  teacherType: 1,
  teacherId: undefined,
  teacherName: '',
  teacherOrg: '',
  hours: 0,
  deptIds: '',
  series: '',
  required: 1,
  passScore: 60,
  cost: 0,
  status: 1,
  remark: '',
})

const rules: FormRules = {
  courseCode: [{ required: true, message: '请输入课程编码', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  hours: [{ required: true, message: '请输入学时', trigger: 'blur' }],
}

function resetForm() {
  Object.assign(form, {
    id: null,
    courseCode: '',
    courseName: '',
    trainType: 1,
    teacherType: 1,
    teacherId: undefined,
    teacherName: '',
    teacherOrg: '',
    hours: 0,
    deptIds: '',
    series: '',
    required: 1,
    passScore: 60,
    cost: 0,
    status: 1,
    remark: '',
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  visible.value = true
}

function openEdit(row: TrainCourseVO) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    courseCode: row.courseCode,
    courseName: row.courseName,
    trainType: row.trainType || 1,
    teacherType: row.teacherType || 1,
    teacherId: row.teacherId,
    teacherName: row.teacherName || '',
    teacherOrg: row.teacherOrg || '',
    hours: row.hours ?? 0,
    deptIds: row.deptIds || '',
    series: row.series || '',
    required: row.required ?? 1,
    passScore: row.passScore ?? 60,
    cost: row.cost ?? 0,
    status: row.status || 1,
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
    await saveCourseApi({ ...form, id: editingId.value ?? undefined })
    ElMessage.success(editingId.value ? '已保存' : '已新增')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveCourse', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="visible" :title="editingId ? '编辑课程' : '新增课程'" width="600px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="课程编码" prop="courseCode">
        <el-input v-model="form.courseCode" maxlength="50" />
      </el-form-item>
      <el-form-item label="课程名称" prop="courseName">
        <el-input v-model="form.courseName" maxlength="100" />
      </el-form-item>
      <el-form-item label="课程类型">
        <el-select v-model="form.trainType" style="width: 100%">
          <el-option v-for="o in TRAIN_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="讲师类型">
        <el-select v-model="form.teacherType" style="width: 100%">
          <el-option v-for="o in TRAIN_TEACHER_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.teacherType === 1" label="内部讲师">
        <el-select v-model="form.teacherId" clearable filterable placeholder="请选择" style="width: 100%">
          <el-option v-for="u in props.userCandidates" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.teacherType === 2" label="讲师姓名">
        <el-input v-model="form.teacherName" maxlength="50" />
      </el-form-item>
      <el-form-item v-if="form.teacherType === 2" label="讲师机构">
        <el-input v-model="form.teacherOrg" maxlength="100" />
      </el-form-item>
      <el-form-item label="学时" prop="hours">
        <el-input-number v-model="form.hours" :min="0" :max="9999" :precision="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="是否必修">
        <el-select v-model="form.required" style="width: 100%">
          <el-option v-for="o in TRAIN_REQUIRED_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="及格线">
        <el-input-number v-model="form.passScore" :min="0" :max="100" :precision="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="培训成本">
        <el-input-number v-model="form.cost" :min="0" :max="99999999" controls-position="right" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="o in TRAIN_COURSE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
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
