<script setup lang="ts">
/**
 * 表单预览（独立页，供流程模板「查看表单」跳转）。
 *
 * <p>以只读态渲染指定表单的规则，与「表单管理」列表里的弹窗预览区别在于：
 * 这个页面有独立路由，可被流程模板页直接链接，也不带任何编辑入口。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFormDetailApi, getFormOptionsApi } from '@/api/form'
import { logAsyncError } from '@/utils/asyncGuard'
import FormRender from '@/components/form/FormRender.vue'
import type { FormVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const formOptions = ref<FormVO[]>([])
const target = ref<FormVO | null>(null)
const formCode = computed(() => (route.query.formCode ? String(route.query.formCode) : ''))

async function loadByCode(code: string) {
  if (!code) {
    return
  }
  try {
    // 优先用 options 里的完整对象（列表接口不返回 ruleJson，故仍需按 id 取详情）
    const hit = formOptions.value.find((f) => f.formCode === code)
    if (hit) {
      target.value = await getFormDetailApi(hit.id)
    }
  } catch (error) {
    logAsyncError('加载表单详情失败', error)
  }
}

async function onSelect(code: string) {
  await loadByCode(code)
}

onMounted(async () => {
  try {
    formOptions.value = await getFormOptionsApi()
  } catch (error) {
    logAsyncError('加载表单选项失败', error)
  }
  await loadByCode(formCode.value)
})

watch(formCode, (code) => loadByCode(code))
</script>

<template>
  <div class="form-preview">
    <el-card shadow="never">
      <div class="form-preview__bar">
        <el-select
          :model-value="formCode"
          placeholder="选择要预览的表单"
          filterable
          style="width: 360px"
          @change="onSelect"
        >
          <el-option
            v-for="f in formOptions"
            :key="f.formCode"
            :label="`${f.formName}（${f.formCode}）`"
            :value="f.formCode"
          />
        </el-select>
        <el-button @click="router.back()">返回</el-button>
      </div>

      <el-descriptions v-if="target" :column="3" border class="form-preview__meta">
        <el-descriptions-item label="表单编码">{{ target.formCode }}</el-descriptions-item>
        <el-descriptions-item label="表单名称">{{ target.formName }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ target.category || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">表单内容（只读）</el-divider>
      <FormRender v-if="target" :rule-json="target.ruleJson" disabled />
    </el-card>
  </div>
</template>

<style scoped>
.form-preview__bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.form-preview__meta {
  margin-bottom: 8px;
}
</style>
