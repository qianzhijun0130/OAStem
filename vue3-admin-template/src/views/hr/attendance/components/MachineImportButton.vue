<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { importMachineAttendanceApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { MachineImportResult } from '@/api/types'

/**
 * 考勤机数据导入入口（BIZ-OPT-08）。
 *
 * <p>自包含：按钮 + 隐藏文件框 + 结果弹窗都在这里，页面只挂一行。
 * 之所以不塞进 index.vue——那个文件已经 650 行，且导入与「看考勤明细」是两件事。</p>
 *
 * <p>结果弹窗是必需的：导入<b>不做全量回滚</b>，坏行不阻断整批，
 * 所以「接口没报错」不等于「全部成功」。必须把 成功/重复/失败 三个数与逐行原因摊开，
 * 否则 HR 只会看到一句「导入完成」，然后在月结时才发现少了几天数据。</p>
 */
const emit = defineEmits<{ imported: [] }>()

const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const visible = ref(false)
const result = ref<MachineImportResult | null>(null)

function pick() {
  fileInput.value?.click()
}

async function onChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploading.value = true
  try {
    const data = await importMachineAttendanceApi(file)
    result.value = data
    visible.value = true
    if (data.imported > 0) {
      ElMessage.success(`已写入 ${data.imported} 人天考勤`)
      emit('imported')
    } else if (data.failed === 0 && data.duplicate > 0) {
      ElMessage.info('数据与已有记录一致，无需重复导入')
    } else if (data.failed > 0) {
      ElMessage.warning(`有 ${data.failed} 行未导入，请查看明细`)
    }
  } catch (error) {
    logAsyncError('importMachineAttendance', error)
  } finally {
    uploading.value = false
    // 清空，允许再次选择同一个文件：导完发现漏了几天、修正文件后再传一遍是常规操作
    input.value = ''
  }
}
</script>

<template>
  <span class="machine-import">
    <el-button v-permission="'hr:attendance:import'" :loading="uploading" @click="pick">
      导入考勤机数据
    </el-button>
    <input
      ref="fileInput"
      type="file"
      accept=".xlsx,.xls"
      class="machine-import__file"
      @change="onChange"
    />
  </span>

  <ResponsiveDialog v-model="visible" title="考勤机数据导入结果" width="680px">
    <template v-if="result">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="读取行数">{{ result.total }}</el-descriptions-item>
        <el-descriptions-item label="写入人天">
          <span :class="{ 'machine-import__ok': result.imported > 0 }">{{ result.imported }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="重复跳过">{{ result.duplicate }}</el-descriptions-item>
        <el-descriptions-item label="失败行数">
          <span :class="{ 'machine-import__bad': result.failed > 0 }">{{ result.failed }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <p class="machine-import__hint">
        同一份文件重复上传不会产生重复记录；涉及已封账月份时整批拒绝，需先由超级管理员解封。
      </p>

      <div v-if="result.errors.length" class="machine-import__block">
        <div class="machine-import__title">未导入的行（共 {{ result.failed }} 条，最多显示 50 条）</div>
        <el-table :data="result.errors" border size="small" max-height="240">
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="message" label="原因" show-overflow-tooltip />
        </el-table>
      </div>

      <div v-if="result.warnings.length" class="machine-import__block">
        <div class="machine-import__title">已保留原值的行</div>
        <el-table :data="result.warnings" border size="small" max-height="200">
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="message" label="说明" show-overflow-tooltip />
        </el-table>
      </div>
    </template>
    <template #footer>
      <el-button type="primary" @click="visible = false">知道了</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped>
.machine-import__file {
  display: none;
}

.machine-import__ok {
  color: var(--el-color-success);
  font-weight: 600;
}

.machine-import__bad {
  color: var(--el-color-danger);
  font-weight: 600;
}

.machine-import__hint {
  margin: 12px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.machine-import__block {
  margin-top: 12px;
}

.machine-import__title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}
</style>
