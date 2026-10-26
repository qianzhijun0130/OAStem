<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { importFlowApi } from '@/api/flow'
import { logAsyncError } from '@/utils/asyncGuard'
import type { DictDataVO } from '@/api/types'

/**
 * 导入 BPMN 生成流程模板（FE-OPT-05 拆分自 index.vue）。
 *
 * <p>自带弹窗可见性与文件解析；导入成功后只 emit `imported(id)`，
 * 由父页面决定「刷新列表 + 进入设计器补配审批人」，组件不反向调父级函数。</p>
 */
const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
const DEFAULT_PROCESS_ID = /^[Pp]rocess_?\d*$/

const props = defineProps<{
  /** 流程分类下拉（字典 flow_category，由父页面统一加载，避免组件重复请求） */
  categoryOptions: DictDataVO[]
}>()

const emit = defineEmits<{ imported: [id: number] }>()

// ---------------- 导入 BPMN ----------------
const importVisible = ref(false)
const importing = ref(false)
const importFileName = ref('')
const importXml = ref('')
const importSummary = ref('')

const importForm = reactive({ flowCode: '', flowName: '', category: '' })

function open() {
  importVisible.value = true
  importFileName.value = ''
  importXml.value = ''
  importSummary.value = ''
  Object.assign(importForm, { flowCode: '', flowName: '', category: '' })
}

/**
 * 前端先读一遍 XML，用于预填流程编码/名称并提示节点数。
 * 这里只是「预填」——后端仍会用权威解析器重新解析，前端不做审批人判定。
 */
function parseBpmnMeta(xml: string) {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length > 0) {
    return null
  }
  const process = doc.getElementsByTagNameNS(BPMN_NS, 'process')[0]
  if (!process) {
    return null
  }
  return {
    key: process.getAttribute('id') ?? '',
    name: process.getAttribute('name') ?? '',
    nodeCount: doc.getElementsByTagNameNS(BPMN_NS, 'userTask').length,
  }
}

function handleImportFile(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) {
    return
  }
  importFileName.value = file.name
  void file.text().then((text) => {
    importXml.value = text
    const meta = parseBpmnMeta(text)
    if (!meta) {
      importSummary.value = '无法解析该文件，请确认是合法的 BPMN 2.0 XML'
      return
    }
    // 默认标识（process_1）没有业务含义，不预填而是提醒用户自己起名——
    // 流程编码保存后不可修改，直接沿用会让模板列表里出现一堆 process_1
    const isDefaultId = DEFAULT_PROCESS_ID.test(meta.key)
    if (!importForm.flowCode && !isDefaultId) {
      importForm.flowCode = meta.key
    }
    if (!importForm.flowName) {
      importForm.flowName = meta.name
    }
    importSummary.value = isDefaultId
      ? `文件用的是默认流程标识 ${meta.key}，请自行填写流程编码；用户任务 ${meta.nodeCount} 个`
      : `process id = ${meta.key}，用户任务 ${meta.nodeCount} 个`
  })
}

async function submitImport() {
  if (!importXml.value) {
    ElMessage.warning('请先选择 BPMN 文件')
    return
  }
  importing.value = true
  try {
    const id = await importFlowApi({
      bpmnXml: importXml.value,
      flowCode: importForm.flowCode.trim() || undefined,
      flowName: importForm.flowName.trim() || undefined,
      category: (importForm.category || '').trim() || undefined,
    })
    ElMessage.success('导入成功，流程已部署')
    importVisible.value = false
    // 交给父页面决定后续：刷新列表并进入设计器补配审批人
    emit('imported', id)
  } catch (error) {
    logAsyncError('导入 BPMN 失败', error)
  } finally {
    importing.value = false
  }
}

defineExpose({ open })
</script>

<template>
    <ResponsiveDialog
      v-model="importVisible"
      title="导入 BPMN 生成流程模板"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="import__tip"
        title="节点审批人按约定自动解析；未按约定标注的节点，导入后在设计器里补配即可"
        description="约定一（Flowable 标准属性）：flowable:candidateGroups=&quot;leader&quot; 部门主管，&quot;role:角色id&quot; 指定角色，flowable:candidateUsers=&quot;用户id&quot; 指定人员。约定二（扩展元素）：oa:assignee type=&quot;user|role|leader&quot; value=&quot;...&quot;。"
      />
      <el-form label-width="90px">
        <el-form-item label="BPMN 文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".bpmn,.xml"
            :on-change="handleImportFile"
          >
            <el-button>选择文件</el-button>
          </el-upload>
          <span v-if="importFileName" class="import__file">{{ importFileName }}</span>
        </el-form-item>
        <el-form-item label="流程编码">
          <el-input v-model="importForm.flowCode" placeholder="留空则取文件的 process id，建议手动填写" />
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="importForm.flowName" placeholder="留空取 BPMN 的 process name" />
        </el-form-item>
        <el-form-item label="分类">
          <!-- 同上：与 flow_category 字典同源，导入时不接受字典外的分类 -->
          <el-select v-model="importForm.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="解析结果">
          <span class="designer__muted">{{ importSummary || '请先选择 BPMN 文件' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入并部署</el-button>
      </template>
    </ResponsiveDialog>
</template>
