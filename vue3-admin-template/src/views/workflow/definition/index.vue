<script setup lang="ts">
/**
 * 流程模板管理：列表 + 行操作（新增/编辑/发布/下架/删除/导入入口）。
 *
 * <p>设计器与导入弹窗已拆到 `components/DesignerDialog.vue`：那一侧自包含全部设计态
 * （表单、节点配置、分支条件、表单绑定、字段权限、保存与发布校验），本页只持有列表，
 * 通过 ref 调用它的方法，并通过 `@saved` 在保存后刷新列表。</p>
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFlowApi, updateFlowStatusApi } from '@/api/flow'
import { getDictDataByTypeApi } from '@/api/dict'
import type { DictDataVO, FlowDefinitionVO } from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'
import DefinitionTable from './components/DefinitionTable.vue'
import DesignerDialog from './components/DesignerDialog.vue'
import { useDefinitionList } from './composables/useDefinitionList'

const {
  loading,
  list,
  total,
  query,
  loadPage,
  handleSizeChange,
  handlePageChange,
  resetQuery,
} = useDefinitionList()

/** 流程分类下拉：列表筛选项与设计器内的分类选择共用同一份字典 */
const categoryOptions = ref<DictDataVO[]>([])

/** 设计器弹窗：设计态全部在组件内，页面只调它的方法 */
const designerRef = ref<InstanceType<typeof DesignerDialog> | null>(null)

function openCreate() {
  designerRef.value?.openCreate()
}

function openImport() {
  designerRef.value?.openImport()
}

function openEdit(row: FlowDefinitionVO) {
  void designerRef.value?.openEdit(row)
}

async function remove(row: FlowDefinitionVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除流程模板「${row.flowName}」吗？该操作会同时卸载 Flowable 中的流程定义。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteFlowApi(row.id)
  ElMessage.success('删除成功')
  await loadPage()
}

/** 流程模板状态：0草稿/未发布 1已发布（复用 oa_flow_definition.status，无 DDL） */
const FLOW_STATUS_DRAFT = 0
const FLOW_STATUS_PUBLISHED = 1

/**
 * 发布流程模板。
 *
 * <p>重复操作防护：已发布模板不显示发布按钮，此处再兜一层，
 * 避免并发点击或旧页面状态导致重复请求。</p>
 */
async function publishFlow(row: FlowDefinitionVO) {
  if (row.status === FLOW_STATUS_PUBLISHED) {
    ElMessage.info('该模板已发布')
    return
  }
  // 校验依赖设计器内实时画布：由设计器组件负责「必要时打开并加载 → 同步节点 → 校验」，
  // 页面只接收校验结果，避免页面持有设计器状态
  const err = await designerRef.value?.preparePublish(row)
  if (err) {
    ElMessage.warning(err)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认发布流程「${row.flowName}」？发布后该模板将出现在员工的「发起申请」下拉中。`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await applyStatus(row, FLOW_STATUS_PUBLISHED, '已发布')
  designerRef.value?.close()
  await loadPage()
}

/** 下架流程模板：确认后立即生效，员工侧下拉不再显示 */
async function unpublishFlow(row: FlowDefinitionVO) {
  if (row.status !== FLOW_STATUS_PUBLISHED) {
    ElMessage.info('该模板未处于发布状态')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认下架流程「${row.flowName}」？下架后员工将无法再发起该流程，已发起的审批单不受影响。`,
      '下架确认',
      { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await applyStatus(row, FLOW_STATUS_DRAFT, '已下架')
}

/** 统一切换状态：就地改行数据，避免整表重拉（与项目「保存不重载」约定一致） */
async function applyStatus(row: FlowDefinitionVO, status: number, tip: string) {
  try {
    await updateFlowStatusApi(row.id, status)
    row.status = status
    ElMessage.success(tip)
  } catch (error) {
    logAsyncError('切换流程模板状态失败', error)
  }
}

onMounted(async () => {
  try {
    categoryOptions.value = await getDictDataByTypeApi('flow_category')
  } catch (error) {
    logAsyncError('加载流程分类字典失败', error)
  }
  await loadPage()
})
</script>

<template>
  <div class="flow-def">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="loadPage">
        <el-form-item label="流程名称">
          <el-input v-model="query.flowName" placeholder="请输入流程名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPage">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="flow-def__toolbar">
        <el-button v-permission="'system:flow:add'" type="primary" @click="openCreate">
          新增流程模板
        </el-button>
        <el-button v-permission="'system:flow:add'" @click="openImport">导入 BPMN</el-button>
        <span class="designer__muted">
          用任意 BPMN 工具画好后导入，或直接用内置样例（请假 / 报销）
        </span>
      </div>

      <DefinitionTable
        :list="list"
        :loading="loading"
        @edit="openEdit"
        @publish="publishFlow"
        @unpublish="unpublishFlow"
        @remove="remove"
      />

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="flow-def__pagination"
      />
    </el-card>

    <DesignerDialog ref="designerRef" :category-options="categoryOptions" @saved="loadPage" />

  </div>
</template>

<style src="./definition.css"></style>
