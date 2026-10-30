<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadPerfImportTemplateApi, importPerfResultsApi } from '@/api/perf'
import { logAsyncError } from '@/utils/asyncGuard'
import type { PerfImportResult } from '@/api/types'

/**
 * 绩效结果导入入口（BIZ-OPT-10 的历史迁移部分）。
 *
 * <p>两个按钮成对出现：<b>先下载模板再导入</b>。模板按周期生成（每个方案的指标不同），
 * 列名带权重后缀，导入时用它校验文件与方案是否还对得上——方案改过权重后拿旧模板导入
 * 会被直接拒绝，而不是按旧权重折算出一批错数。</p>
 *
 * <p>结果弹窗是必需的：导入不做全量回滚（坏行不阻断整批），
 * 所以「接口没报错」≠「全部成功」。不把逐行原因摊开，HR 只会看到一句「导入完成」，
 * 然后发现某些人的等级是空的。</p>
 */
const props = defineProps<{ cycleId?: number }>()

/** 导入成功后让列表刷新——否则 HR 会以为没导进去 */
const emit = defineEmits<{ imported: [] }>()

const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const downloading = ref(false)
const visible = ref(false)
const result = ref<PerfImportResult | null>(null)

function pick() {
  if (!props.cycleId) {
    ElMessage.warning('请先选择考核周期')
    return
  }
  fileInput.value?.click()
}

async function downloadTemplate() {
  if (!props.cycleId) {
    ElMessage.warning('请先选择考核周期')
    return
  }
  downloading.value = true
  try {
    await downloadPerfImportTemplateApi(props.cycleId)
  } catch (error) {
    logAsyncError('downloadPerfImportTemplate', error)
  } finally {
    downloading.value = false
  }
}

async function onChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !props.cycleId) {
    return
  }
  uploading.value = true
  try {
    const data = await importPerfResultsApi(props.cycleId, file)
    result.value = data
    visible.value = true
    if (data.imported > 0) {
      emit('imported')
      ElMessage.success(`已导入 ${data.imported} 人${data.updated ? `（其中覆盖 ${data.updated} 人）` : ''}`)
    } else if (data.failed > 0) {
      ElMessage.warning(`有 ${data.failed} 行未导入，请查看明细`)
    }
  } catch (error) {
    logAsyncError('importPerfResults', error)
  } finally {
    uploading.value = false
    // 清空，允许再次选择同一个文件（改完错误行再传一遍是常规操作）
    input.value = ''
  }
}
</script>

<template>
  <span class="perf-import">
    <el-button
      v-permission="'hr:performance:import'"
      :loading="downloading"
      data-action="download-template"
      @click="downloadTemplate"
    >
      下载模板
    </el-button>
    <el-button
      v-permission="'hr:performance:import'"
      :loading="uploading"
      data-action="import"
      @click="pick"
    >
      导入结果
    </el-button>
    <input
      ref="fileInput"
      type="file"
      accept=".xlsx,.xls"
      class="perf-import__file"
      @change="onChange"
    />
  </span>

  <ResponsiveDialog v-model="visible" title="绩效结果导入结果" width="680px">
    <template v-if="result">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="读取行数">{{ result.total }}</el-descriptions-item>
        <el-descriptions-item label="导入人数">
          <span :class="{ 'perf-import__ok': result.imported > 0 }">{{ result.imported }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="覆盖已有">{{ result.updated }}</el-descriptions-item>
        <el-descriptions-item label="失败行数">
          <span :class="{ 'perf-import__bad': result.failed > 0 }">{{ result.failed }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <p class="perf-import__hint">
        导入的成绩来源标记为「导入」，与系统评分的记录区分；总分由系统按
        Σ(得分×权重)+加分−扣分 计算，封顶 120、封底 0。
      </p>

      <div v-if="result.errors.length" class="perf-import__block">
        <div class="perf-import__title">未导入的行（共 {{ result.failed }} 条，最多显示 50 条）</div>
        <el-table :data="result.errors" border size="small" max-height="240">
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="message" label="原因" show-overflow-tooltip />
        </el-table>
      </div>
    </template>
    <template #footer>
      <el-button type="primary" @click="visible = false">知道了</el-button>
    </template>
  </ResponsiveDialog>
</template>

<style scoped>
.perf-import__file {
  display: none;
}

.perf-import__ok {
  color: var(--el-color-success);
  font-weight: 600;
}

.perf-import__bad {
  color: var(--el-color-danger);
  font-weight: 600;
}

.perf-import__hint {
  margin: 12px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.perf-import__block {
  margin-top: 12px;
}

.perf-import__title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}
</style>
