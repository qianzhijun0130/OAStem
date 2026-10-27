<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveSocialPolicyApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { SocialPolicyVO } from '@/api/types'

/**
 * 社保公积金政策表单弹窗（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>默认值即「常见地区起步值」，新增政策时多数只需改基数和少数比例。
 * 比例一律用小数（0.08 = 8%），与后端 `oa_social_policy` 口径一致。</p>
 */
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const form = reactive<Partial<SocialPolicyVO>>({
  city: '默认',
  policyYear: new Date().getFullYear(),
  baseMin: 0,
  baseMax: 0,
  fundBaseMin: 0,
  fundBaseMax: 0,
  pensionPersonal: 0.08,
  pensionCompany: 0.16,
  medicalPersonal: 0.02,
  medicalCompany: 0.098,
  unemploymentPersonal: 0.005,
  unemploymentCompany: 0.005,
  injuryCompany: 0.002,
  maternityCompany: 0.008,
  fundPersonal: 0.12,
  fundCompany: 0.12,
  remark: '',
})

function open(row?: SocialPolicyVO) {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, {
      id: undefined,
      city: '默认',
      policyYear: new Date().getFullYear(),
      baseMin: 0,
      baseMax: 0,
      fundBaseMin: 0,
      fundBaseMax: 0,
      pensionPersonal: 0.08,
      pensionCompany: 0.16,
      medicalPersonal: 0.02,
      medicalCompany: 0.098,
      unemploymentPersonal: 0.005,
      unemploymentCompany: 0.005,
      injuryCompany: 0.002,
      maternityCompany: 0.008,
      fundPersonal: 0.12,
      fundCompany: 0.12,
      remark: '',
    })
  }
  visible.value = true
}

async function submit() {
  submitting.value = true
  try {
    await saveSocialPolicyApi({ ...form } as SocialPolicyVO)
    ElMessage.success('已保存')
    visible.value = false
    emit('saved')
  } catch (error) {
    logAsyncError('saveSocialPolicy', error)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="社保公积金政策" width="720px">
    <el-form :model="form" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="城市">
            <el-input v-model="form.city" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="年度">
            <el-input-number v-model="form.policyYear" :min="2000" :max="2100" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="社保基数下限">
            <el-input-number v-model="form.baseMin" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="社保基数上限">
            <el-input-number v-model="form.baseMax" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="公积金下限">
            <el-input-number v-model="form.fundBaseMin" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="公积金上限">
            <el-input-number v-model="form.fundBaseMax" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">缴费比例（0.08 = 8%）</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="养老个人">
            <el-input-number v-model="form.pensionPersonal" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="养老单位">
            <el-input-number v-model="form.pensionCompany" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="医疗个人">
            <el-input-number v-model="form.medicalPersonal" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="医疗单位">
            <el-input-number v-model="form.medicalCompany" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="失业个人">
            <el-input-number v-model="form.unemploymentPersonal" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="失业单位">
            <el-input-number v-model="form.unemploymentCompany" :min="0" :max="1" :step="0.005" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="工伤单位">
            <el-input-number v-model="form.injuryCompany" :min="0" :max="1" :step="0.001" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="生育单位">
            <el-input-number v-model="form.maternityCompany" :min="0" :max="1" :step="0.001" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="公积金个人">
            <el-input-number v-model="form.fundPersonal" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="公积金单位">
            <el-input-number v-model="form.fundCompany" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
