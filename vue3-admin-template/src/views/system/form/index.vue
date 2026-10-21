<script setup lang="ts">
/**
 * 表单管理 · 列表。
 *
 * <p>低代码表单的入口页：查看 / 新增 / 编辑 / 启停 / 删除 / 进入设计器 / 预览。</p>
 *
 * <p>权限码：页面 {@code system:form:query}；行内操作按
 * {@code system:form:add|edit|delete|design|preview} 用 v-permission 控制显隐。</p>
 *
 * <p><b>保存不重载约定</b>：启停与删除成功后只做局部更新（就地改状态 / 本地移除行），
 * 不整页重拉；确需校正数据时静默调用 {@code load()}，而 {@code query} 对象全程不变，
 * 因此筛选条件与页码始终保留。详见 {@code composables/useSaveState.ts}。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getFormPageApi,
  updateFormStatusApi,
  deleteFormApi,
} from '@/api/form'
import { logAsyncError } from '@/utils/asyncGuard'
import { useSaveState } from '@/composables/useSaveState'
import FormRender from '@/components/form/FormRender.vue'
import type { FormVO } from '@/api/types'

const router = useRouter()

const loading = ref(false)
const total = ref(0)
const rows = ref<FormVO[]>([])

/** 统一保存控制器：保证保存后不重载、失败零副作用 */
const saver = useSaveState()

const query = reactive({
  keyword: '',
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10,
})

/** 预览弹窗上下文 */
const previewVisible = ref(false)
const previewTarget = ref<FormVO | null>(null)

/**
 * 拉取列表。
 *
 * @param silent 静默模式：保存后的校正刷新用。不显示 loading 遮罩，
 *   避免表格闪一下造成「页面在重载」的错觉 —— 这是「不重载」体验的关键细节。
 */
async function load(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    const res = await getFormPageApi({ ...query })
    rows.value = res.records
    total.value = res.total
  } catch (error) {
    logAsyncError('加载表单列表失败', error)
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

function search() {
  query.pageNo = 1
  load()
}

function resetQuery() {
  query.keyword = ''
  query.status = undefined
  query.pageNo = 1
  load()
}

async function toggleStatus(row: FormVO) {
  const next = row.status === 1 ? 0 : 1
  // 启停只影响当前行，就地改状态而非整页重拉 —— 筛选条件、页码、滚动位置全部保持不变
  const ok = await saver.run(() => updateFormStatusApi(row.id, next), {
    label: '切换表单状态',
    onSuccess: null,
  })
  if (ok) {
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已停用')
  }
}

async function remove(row: FormVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除表单「${row.formName}」？删除后原编码可立即复用。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消
    return
  }
  // 删除必须重拉（行数变了），但保留筛选条件与页码：
  // 只有「删空了当前页」才回退一页，否则停在当前页不动。
  const ok = await saver.run(
    async () => {
      await deleteFormApi(row.id)
      // 本地先行移除，避免重拉期间页面闪一下旧数据
      const idx = rows.value.findIndex((r) => r.id === row.id)
      if (idx >= 0) {
        rows.value.splice(idx, 1)
        total.value -= 1
      }
      if (rows.value.length === 0 && query.pageNo > 1) {
        query.pageNo -= 1
      }
    },
    { label: '删除表单', onSuccess: null },
  )
  if (ok) {
    ElMessage.success('已删除')
    // 静默重拉校正：query 对象保持不变，故筛选条件与页码天然保留
    await load(true)
  }
}

function openDesigner(row?: FormVO) {
  // 设计器与编辑同页：带 id 进编辑态，不带 id 进新建态
  router.push({
    path: '/system/form/designer',
    query: row ? { id: String(row.id) } : {},
  })
}

function openPreview(row: FormVO) {
  previewTarget.value = row
  previewVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="form-list">
    <el-card shadow="never">
      <div class="form-list__bar">
        <el-input
          v-model="query.keyword"
          placeholder="表单编码 / 名称"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <div class="form-list__spacer" />
        <el-button v-permission="'system:form:add'" type="primary" @click="openDesigner()">
          新增表单
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="formCode" label="表单编码" min-width="140" />
        <el-table-column prop="formName" label="表单名称" min-width="140" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="引用" width="80" align="center">
          <template #default="{ row }">
            <!-- bindCount > 0 时删除会被后端 2504 拒绝，这里提前给出提示 -->
            <el-tooltip v-if="row.bindCount > 0" content="已被流程节点引用，不可删除">
              <el-tag type="warning">{{ row.bindCount }}</el-tag>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button icon-only link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button icon-only
              v-permission="'system:form:design'"
              link
              type="primary"
              @click="openDesigner(row)"
            >
              设计
            </el-button>
            <el-button icon-only
              v-permission="'system:form:edit'"
              link
              type="primary"
              @click="openDesigner(row)"
            >
              编辑
            </el-button>
            <el-button icon-only
              v-permission="'system:form:edit'"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button icon-only
              v-permission="'system:form:delete'"
              link
              type="danger"
              :disabled="row.bindCount > 0"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无表单，点击右上角「新增表单」用设计器创建" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="search"
        @current-change="load"
      />
    </el-card>

    <ResponsiveDialog v-model="previewVisible" title="表单预览" width="720px">
      <FormRender v-if="previewTarget" :rule-json="previewTarget.ruleJson" disabled />
    </ResponsiveDialog>
  </div>
</template>

<style scoped>
.form-list__bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.form-list__spacer {
  flex: 1;
}
</style>
