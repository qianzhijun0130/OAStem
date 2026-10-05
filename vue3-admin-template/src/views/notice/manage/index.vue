<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deleteNoticeApi,
  getNoticeDetailApi,
  getNoticePageApi,
  offlineNoticeApi,
  publishNoticeApi,
  saveNoticeApi,
  updateNoticeTopApi,
} from '@/api/notice'
import { getDictDataByTypeApi } from '@/api/dict'
import { getDeptTreeApi } from '@/api/dept'
import { getUserPageApi } from '@/api/user'
import { uploadFileApi } from '@/api/file'
import type {
  DeptVO,
  DictDataVO,
  FileUploadVO,
  NoticePageQuery,
  NoticeVO,
  UserVO,
} from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'

/** 发布范围 */
const SCOPE_ALL = 1
const SCOPE_DEPT = 2
const SCOPE_USER = 3

/** 公告状态 */
const STATUS_DRAFT = 0
const STATUS_PUBLISHED = 1
const STATUS_OFFLINE = 2

const loading = ref(false)
const list = ref<NoticeVO[]>([])
const total = ref(0)

const query = reactive<NoticePageQuery>({
  pageNo: 1,
  pageSize: 10,
  noticeTitle: undefined,
  noticeType: undefined,
  noticeLevel: undefined,
  noticeStatus: undefined,
})

/** 公告类型 / 级别取自数据字典，新增枚举值只需在字典管理里加一条 */
const typeOptions = ref<DictDataVO[]>([])
const levelOptions = ref<DictDataVO[]>([])
const deptTree = ref<DeptVO[]>([])
const userOptions = ref<UserVO[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

interface NoticeFormModel {
  noticeTitle: string
  noticeType: number
  noticeLevel: number
  content: string
  publishScope: number
  scopeDeptIds: number[]
  scopeUserIds: number[]
  topFlag: number
  /** 计划发布时间（ISO 字符串）：到点自动发布 */
  scheduledPublishTime: string | null
  /** 自动下架时间：到点自动下架 */
  expireTime: string | null
  attachments: FileUploadVO[]
}

const form = reactive<NoticeFormModel>(createEmptyForm())

const formRules: FormRules = {
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}

function createEmptyForm(): NoticeFormModel {
  return {
    noticeTitle: '',
    noticeType: 1,
    noticeLevel: 2,
    content: '',
    publishScope: SCOPE_ALL,
    scopeDeptIds: [],
    scopeUserIds: [],
    topFlag: 0,
    scheduledPublishTime: null,
    expireTime: null,
    attachments: [],
  }
}

async function loadList() {
  loading.value = true
  try {
    const page = await getNoticePageApi(query)
    list.value = page.records
    total.value = Number(page.total)
  } catch (error) {
    logAsyncError('加载公告列表失败', error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadList()
}

function handleReset() {
  query.noticeTitle = undefined
  query.noticeType = undefined
  query.noticeLevel = undefined
  query.noticeStatus = undefined
  handleSearch()
}

async function loadOptions() {
  try {
    const [types, levels, depts, users] = await Promise.all([
      getDictDataByTypeApi('notice_type'),
      getDictDataByTypeApi('notice_level'),
      getDeptTreeApi(),
      getUserPageApi({ pageNo: 1, pageSize: 200 }),
    ])
    typeOptions.value = types
    levelOptions.value = levels
    deptTree.value = depts
    userOptions.value = users.records
  } catch (error) {
    logAsyncError('加载公告表单选项失败', error)
  }
}

/* ---------- 新增 / 编辑 ---------- */

async function openDialog(row?: NoticeVO) {
  try {
    editingId.value = row?.id ?? null
    Object.assign(form, createEmptyForm())

    if (row) {
      const detail = await getNoticeDetailApi(row.id)
      Object.assign(form, {
        noticeTitle: detail.noticeTitle,
        noticeType: detail.noticeType,
        noticeLevel: detail.noticeLevel,
        content: detail.content,
        publishScope: detail.publishScope,
        scopeDeptIds: detail.scopeDeptIds ?? [],
        scopeUserIds: detail.scopeUserIds ?? [],
        topFlag: detail.topFlag,
        scheduledPublishTime: detail.scheduledPublishTime ?? null,
        expireTime: detail.expireTime ?? null,
        attachments: (detail.attachments ?? []).map((item) => ({
          id: item.id,
          fileName: item.fileName,
          url: item.url,
          fileSize: item.fileSize ?? 0,
        })),
      })
    }
    dialogVisible.value = true
  } catch (error) {
    logAsyncError('加载公告详情失败', error)
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.publishScope === SCOPE_DEPT && form.scopeDeptIds.length === 0) {
    ElMessage.warning('请至少选择一个部门')
    return
  }
  if (form.publishScope === SCOPE_USER && form.scopeUserIds.length === 0) {
    ElMessage.warning('请至少选择一名人员')
    return
  }

  submitting.value = true
  try {
    await saveNoticeApi({
      id: editingId.value,
      noticeTitle: form.noticeTitle,
      noticeType: form.noticeType,
      noticeLevel: form.noticeLevel,
      content: form.content,
      publishScope: form.publishScope,
      scopeDeptIds: form.publishScope === SCOPE_DEPT ? form.scopeDeptIds : [],
      scopeUserIds: form.publishScope === SCOPE_USER ? form.scopeUserIds : [],
      attachmentIds: form.attachments.map((item) => item.id),
      topFlag: form.topFlag,
      scheduledPublishTime: form.scheduledPublishTime,
      expireTime: form.expireTime,
    })
    ElMessage.success(editingId.value ? '公告已更新' : '公告已保存为草稿')
    dialogVisible.value = false
    await loadList()
  } finally {
    submitting.value = false
  }
}

async function handleAttachmentUpload(options: { file: File }) {
  uploading.value = true
  try {
    const uploaded = await uploadFileApi(options.file as File)
    form.attachments.push(uploaded)
    ElMessage.success('附件已上传')
  } catch (error) {
    logAsyncError('上传公告附件失败', error)
  } finally {
    uploading.value = false
  }
}

/* ---------- 状态流转 ---------- */

async function handlePublish(row: NoticeVO) {
  try {
    await publishNoticeApi(row.id)
    ElMessage.success('公告已发布')
    await loadList()
  } catch (error) {
    logAsyncError('发布公告失败', error)
  }
}

async function handleOffline(row: NoticeVO) {
  try {
    await ElMessageBox.confirm(`确定下架公告「${row.noticeTitle}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await offlineNoticeApi(row.id)
  ElMessage.success('公告已下架')
  await loadList()
}

async function handleToggleTop(row: NoticeVO) {
  try {
    const next = row.topFlag === 1 ? 0 : 1
    await updateNoticeTopApi(row.id, next)
    ElMessage.success(next === 1 ? '已置顶' : '已取消置顶')
    await loadList()
  } catch (error) {
    logAsyncError('置顶操作失败', error)
  }
}

async function handleDelete(row: NoticeVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除公告「${row.noticeTitle}」吗？删除后其阅读记录也会一并清除。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteNoticeApi(row.id)
  ElMessage.success('已删除')
  await loadList()
}

/* ---------- 展示辅助 ---------- */

function typeLabel(value: number): string {
  return typeOptions.value.find((item) => Number(item.itemValue) === value)?.itemLabel ?? String(value)
}

function typeTagClass(value: number): string {
  return typeOptions.value.find((item) => Number(item.itemValue) === value)?.cssClass ?? 'info'
}

function levelLabel(value: number): string {
  return levelOptions.value.find((item) => Number(item.itemValue) === value)?.itemLabel ?? String(value)
}

function levelTagClass(value: number): string {
  return levelOptions.value.find((item) => Number(item.itemValue) === value)?.cssClass ?? 'info'
}

function statusText(status: number): string {
  if (status === STATUS_PUBLISHED) return '已发布'
  if (status === STATUS_OFFLINE) return '已下架'
  return '草稿'
}

function statusTagType(status: number): 'success' | 'info' | 'warning' {
  if (status === STATUS_PUBLISHED) return 'success'
  if (status === STATUS_OFFLINE) return 'warning'
  return 'info'
}

function scopeText(scope: number): string {
  if (scope === SCOPE_DEPT) return '指定部门'
  if (scope === SCOPE_USER) return '指定人员'
  return '全体人员'
}

onMounted(async () => {
  await loadOptions()
  await loadList()
})
</script>

<template>
  <div class="notice-manage">
    <el-card shadow="never">
      <el-form :inline="true" class="notice-manage__filters">
        <el-form-item label="标题">
          <el-input
            v-model="query.noticeTitle"
            placeholder="支持模糊匹配"
            clearable
            class="notice-manage__input"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.noticeType" placeholder="全部" clearable class="notice-manage__select">
            <el-option
              v-for="item in typeOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.noticeLevel" placeholder="全部" clearable class="notice-manage__select">
            <el-option
              v-for="item in levelOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.noticeStatus" placeholder="全部" clearable class="notice-manage__select">
            <el-option label="草稿" :value="STATUS_DRAFT" />
            <el-option label="已发布" :value="STATUS_PUBLISHED" />
            <el-option label="已下架" :value="STATUS_OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="notice-manage__toolbar">
        <el-button v-permission="'system:notice:add'" type="primary" @click="openDialog()">
          新增公告
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.topFlag === 1" size="small" type="danger" class="notice-manage__top">置顶</el-tag>
            {{ row.noticeTitle }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagClass(row.noticeType)">{{ typeLabel(row.noticeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="levelTagClass(row.noticeLevel)">
              {{ levelLabel(row.noticeLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.noticeStatus)">
              {{ statusText(row.noticeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布范围" width="110">
          <template #default="{ row }">{{ scopeText(row.publishScope) }}</template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="110" />
        <el-table-column label="阅读情况" width="110">
          <template #default="{ row }">{{ row.readCount ?? 0 }} 人已读</template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template #default="{ row }">{{ row.publishTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.noticeStatus === STATUS_DRAFT"
              v-permission="'system:notice:add'"
              link
              type="primary"
              size="small"
              @click="openDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.noticeStatus !== STATUS_PUBLISHED"
              v-permission="'system:notice:publish'"
              link
              type="success"
              size="small"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-else
              v-permission="'system:notice:publish'"
              link
              type="warning"
              size="small"
              @click="handleOffline(row)"
            >
              下架
            </el-button>
            <el-button
              v-permission="'system:notice:edit'"
              link
              size="small"
              @click="handleToggleTop(row)"
            >
              {{ row.topFlag === 1 ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button
              v-permission="'system:notice:delete'"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="notice-manage__pagination"
        @current-change="loadList"
        @size-change="handleSearch"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑公告' : '新增公告'"
      width="720px"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="form.noticeTitle" placeholder="请输入标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="公告类型" prop="noticeType">
          <el-select v-model="form.noticeType">
            <el-option
              v-for="item in typeOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="公告级别" prop="noticeLevel">
          <el-select v-model="form.noticeLevel">
            <el-option
              v-for="item in levelOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="请输入公告正文" />
        </el-form-item>
        <el-form-item label="发布范围">
          <el-radio-group v-model="form.publishScope">
            <el-radio :value="SCOPE_ALL">全体人员</el-radio>
            <el-radio :value="SCOPE_DEPT">指定部门</el-radio>
            <el-radio :value="SCOPE_USER">指定人员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.publishScope === SCOPE_DEPT" label="选择部门">
          <el-tree-select
            v-model="form.scopeDeptIds"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            multiple
            show-checkbox
            check-strictly
            placeholder="请选择可见部门"
            class="notice-manage__full"
          />
        </el-form-item>
        <el-form-item v-if="form.publishScope === SCOPE_USER" label="选择人员">
          <el-select
            v-model="form.scopeUserIds"
            multiple
            filterable
            placeholder="请选择可见人员"
            class="notice-manage__full"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.realName || user.username"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <div class="notice-manage__upload">
            <el-upload :show-file-list="false" :http-request="handleAttachmentUpload" multiple>
              <el-button size="small" :loading="uploading">上传附件</el-button>
            </el-upload>
            <ul v-if="form.attachments.length" class="notice-manage__files">
              <li v-for="(file, index) in form.attachments" :key="file.id">
                <span class="notice-manage__file-name">{{ file.fileName }}</span>
                <el-button link type="danger" size="small" @click="form.attachments.splice(index, 1)">
                  移除
                </el-button>
              </li>
            </ul>
          </div>
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.topFlag" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="定时发布">
          <el-date-picker
            v-model="form.scheduledPublishTime"
            type="datetime"
            placeholder="留空表示不启用（可手工发布）"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="自动下架">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="留空表示永不下架"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存为草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notice-manage__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}

.notice-manage__input {
  width: 180px;
}

.notice-manage__select {
  width: 130px;
}

.notice-manage__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.notice-manage__pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.notice-manage__top {
  margin-right: 6px;
}

.notice-manage__full {
  width: 100%;
}

.notice-manage__upload {
  width: 100%;
}

.notice-manage__files {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.notice-manage__files li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 0;
  font-size: 13px;
  color: #606266;
}

.notice-manage__file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
