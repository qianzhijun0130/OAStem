<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLatestNoticeApi, getMyNoticeDetailApi, getMyNoticePageApi, getMyNoticeUnreadCountApi } from '@/api/notice'
import { getDictDataByTypeApi } from '@/api/dict'
import { downloadBlobUrl } from '@/utils/download'
import type { DictDataVO, MyNoticeDetailVO, MyNoticePageQuery, MyNoticeVO } from '@/api/types'
import { logAsyncError } from '@/utils/asyncGuard'

const loading = ref(false)
const list = ref<MyNoticeVO[]>([])
const total = ref(0)
const unreadCount = ref(0)

const typeOptions = ref<DictDataVO[]>([])
const levelOptions = ref<DictDataVO[]>([])

const query = reactive<MyNoticePageQuery>({
  pageNo: 1,
  pageSize: 10,
  noticeTitle: undefined,
  noticeType: undefined,
  readFlag: undefined,
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<MyNoticeDetailVO | null>(null)

async function loadList() {
  loading.value = true
  try {
    const page = await getMyNoticePageApi(query)
    list.value = page.records
    total.value = Number(page.total)
  } catch (error) {
    logAsyncError('加载我的公告失败', error)
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  try {
    unreadCount.value = await getMyNoticeUnreadCountApi()
  } catch (error) {
    logAsyncError('加载未读公告数失败', error)
  }
}

function handleSearch() {
  query.pageNo = 1
  loadList()
}

function handleReset() {
  query.noticeTitle = undefined
  query.noticeType = undefined
  query.readFlag = undefined
  handleSearch()
}

/**
 * 打开详情即视为已读。
 *
 * 后端在详情接口里完成「写阅读记录 + 阅读数自增」，所以列表的未读数必须重新拉取，
 * 且要用新的 readFlag 刷新当前行，否则用户会看到「已读过但状态仍是未读」。
 */
async function openDetail(row: MyNoticeVO) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getMyNoticeDetailApi(row.id)
    row.readFlag = 1
    await loadUnreadCount()
  } catch (error) {
    logAsyncError('加载公告详情失败', error)
  } finally {
    detailLoading.value = false
  }
}

async function handleDownloadAttachment(id: number, fileName: string) {
  try {
    await downloadBlobUrl(`/file/download/${id}`, fileName || '附件')
    ElMessage.success('已开始下载')
  } catch (error) {
    logAsyncError('下载公告附件失败', error)
  }
}

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

function formatSize(size?: number): string {
  if (!size) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

onMounted(async () => {
  const [types, levels, latest] = await Promise.all([
    getDictDataByTypeApi('notice_type'),
    getDictDataByTypeApi('notice_level'),
    getLatestNoticeApi(5),
  ])
  typeOptions.value = types
  levelOptions.value = levels
  // 工作台同源的最新公告，用于首屏感知；列表本身仍是分页查询
  if (latest.length > 0) {
    unreadCount.value = await getMyNoticeUnreadCountApi()
  }
  await loadList()
})
</script>

<template>
  <div class="my-notice">
    <el-card shadow="never">
      <template #header>
        <div class="my-notice__header">
          <span class="my-notice__title">我的公告</span>
          <el-badge v-if="unreadCount > 0" :value="unreadCount" type="danger">
            <el-tag type="warning" size="small" effect="plain">未读 {{ unreadCount }} 条</el-tag>
          </el-badge>
          <el-tag v-else type="success" size="small" effect="plain">已全部阅读</el-tag>
        </div>
      </template>

      <el-form :inline="true" class="my-notice__filters">
        <el-form-item label="标题">
          <el-input
            v-model="query.noticeTitle"
            placeholder="支持模糊匹配"
            clearable
            class="my-notice__input"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.noticeType" placeholder="全部" clearable class="my-notice__select">
            <el-option
              v-for="item in typeOptions"
              :key="item.id"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阅读状态">
          <el-select v-model="query.readFlag" placeholder="全部" clearable class="my-notice__select">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border @row-click="openDetail">
        <el-table-column label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.topFlag === 1" size="small" type="danger" class="my-notice__top">置顶</el-tag>
            <span :class="{ 'my-notice__unread-title': row.readFlag === 0 }">{{ row.noticeTitle }}</span>
            <el-tag v-if="row.readFlag === 0" size="small" type="warning" class="my-notice__badge">未读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagClass(row.noticeType)">{{ typeLabel(row.noticeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="levelTagClass(row.noticeLevel)">{{ levelLabel(row.noticeLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="110" />
        <el-table-column prop="publishTime" label="发布时间" width="170" />
        <el-table-column label="附件" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.hasAttachment === 1" size="small" type="info">有</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button icon-only link type="primary" size="small" @click.stop="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="my-notice__pagination"
        @current-change="loadList"
        @size-change="handleSearch"
      />
    </el-card>

    <el-drawer v-model="drawerVisible" title="公告详情" size="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <h2 class="my-notice__detail-title">{{ detail.noticeTitle }}</h2>
          <div class="my-notice__detail-meta">
            <el-tag size="small" :type="typeTagClass(detail.noticeType)">
              {{ typeLabel(detail.noticeType) }}
            </el-tag>
            <el-tag size="small" :type="levelTagClass(detail.noticeLevel)">
              {{ levelLabel(detail.noticeLevel) }}
            </el-tag>
            <span>{{ detail.publisherName || '-' }}</span>
            <span>{{ detail.publishTime || '-' }}</span>
            <span>阅读 {{ detail.viewCount ?? 0 }} 次</span>
          </div>
          <div class="my-notice__detail-content">{{ detail.content }}</div>

          <div v-if="detail.attachments?.length" class="my-notice__attachments">
            <div class="my-notice__attachments-title">附件</div>
            <div
              v-for="file in detail.attachments"
              :key="file.id"
              class="my-notice__attachment"
              @click="handleDownloadAttachment(file.id, file.fileName)"
            >
              <span class="my-notice__attachment-name">{{ file.fileName }}</span>
              <span class="my-notice__attachment-size">{{ formatSize(file.fileSize) }}</span>
              <el-button link type="primary" size="small">下载</el-button>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.my-notice__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.my-notice__title {
  font-weight: 600;
}

.my-notice__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}

.my-notice__input {
  width: 180px;
}

.my-notice__select {
  width: 130px;
}

.my-notice__pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.my-notice__top {
  margin-right: 6px;
}

.my-notice__badge {
  margin-left: 6px;
}

.my-notice__unread-title {
  font-weight: 600;
}

.my-notice__detail-title {
  margin: 0 0 12px;
  font-size: 20px;
  line-height: 1.4;
}

.my-notice__detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  color: #909399;
}

.my-notice__detail-content {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.my-notice__attachments {
  margin-top: 24px;
}

.my-notice__attachments-title {
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 14px;
}

.my-notice__attachment {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  margin-bottom: 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
}

.my-notice__attachment:hover {
  background-color: #f5f7fa;
}

.my-notice__attachment-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.my-notice__attachment-size {
  font-size: 12px;
  color: #909399;
}
</style>
