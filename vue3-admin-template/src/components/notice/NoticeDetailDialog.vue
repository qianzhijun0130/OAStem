<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyNoticeDetailApi } from '@/api/notice'
import { getDictDataByTypeApi } from '@/api/dict'
import { downloadBlobUrl } from '@/utils/download'
import { logAsyncError } from '@/utils/asyncGuard'
import AppIcon from '@/components/icons/AppIcon.vue'
import type { DictDataVO, MyNoticeDetailVO, NoticeAttachmentVO } from '@/api/types'

/**
 * 公告详情弹窗（读者视角）。
 *
 * <p>打开即调 `/notice/my/{id}`：后端会在同一个事务里写阅读记录 + 阅读数自增，
 * 所以「工作台点开公告」与「我的公告里点开」在已读语义上完全一致。</p>
 *
 * <p>正文是纯文本（发布端是 textarea，不是富文本编辑器），因此用 `white-space: pre-wrap`
 * 保留换行即可，<b>不要</b>用 `v-html` —— 那等于把用户输入当 HTML 执行。</p>
 */
const props = defineProps<{
  modelValue: boolean
  /** 要展示的公告 ID；关闭时保留上一次的值以便动画期间不闪空 */
  noticeId?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 详情加载完成（此时后端已标记已读），父组件可刷新未读数 */
  read: [id: number]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const detail = ref<MyNoticeDetailVO | null>(null)

const typeOptions = ref<DictDataVO[]>([])
const levelOptions = ref<DictDataVO[]>([])
/** 字典是全站共享数据，一个会话内只拉一次 */
const dictReady = ref(false)

/** 字典用于「类型/级别」的中文名与标签色；漏配时退回内置映射，不至于显示成数字 */
const FALLBACK_LEVEL: Record<number, { label: string; type: string }> = {
  1: { label: '普通', type: 'info' },
  2: { label: '重要', type: 'warning' },
  3: { label: '紧急', type: 'danger' },
}

async function ensureDict() {
  if (dictReady.value) {
    return
  }
  try {
    const [types, levels] = await Promise.all([
      getDictDataByTypeApi('notice_type'),
      getDictDataByTypeApi('notice_level'),
    ])
    typeOptions.value = types
    levelOptions.value = levels
    dictReady.value = true
  } catch (error) {
    // 字典失败不影响读公告：下面 labelOf 会退回内置映射
    logAsyncError('加载公告字典失败', error)
  }
}

function labelOf(options: DictDataVO[], value?: number, fallback?: string): string {
  if (value === undefined || value === null) {
    return '-'
  }
  return options.find((item) => Number(item.itemValue) === value)?.itemLabel ?? fallback ?? String(value)
}

function tagTypeOf(options: DictDataVO[], value: number | undefined, fallback: string): string {
  if (value === undefined || value === null) {
    return 'info'
  }
  return options.find((item) => Number(item.itemValue) === value)?.cssClass ?? fallback
}

const typeLabel = computed(() => labelOf(typeOptions.value, detail.value?.noticeType))
const typeTag = computed(() => tagTypeOf(typeOptions.value, detail.value?.noticeType, 'info'))
const levelLabel = computed(() =>
  labelOf(levelOptions.value, detail.value?.noticeLevel, FALLBACK_LEVEL[detail.value?.noticeLevel ?? 0]?.label),
)
const levelTag = computed(() =>
  tagTypeOf(levelOptions.value, detail.value?.noticeLevel, FALLBACK_LEVEL[detail.value?.noticeLevel ?? 0]?.type ?? 'info'),
)

/**
 * 后端返回的是 ISO 时间（2026-10-22T20:30:20），直接展示会被中文字体渲染得很别扭，
 * 统一成「yyyy-MM-dd HH:mm」；秒对阅读公告没有意义，截掉。
 */
function formatTime(value?: string): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
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

async function load() {
  if (!props.noticeId) {
    return
  }
  loading.value = true
  detail.value = null
  try {
    await ensureDict()
    detail.value = await getMyNoticeDetailApi(props.noticeId)
    emit('read', props.noticeId)
  } catch (error) {
    logAsyncError('加载公告详情失败', error)
  } finally {
    loading.value = false
  }
}

async function downloadAttachment(file: NoticeAttachmentVO) {
  try {
    await downloadBlobUrl(`/file/download/${file.id}`, file.fileName || '附件')
    ElMessage.success('已开始下载')
  } catch (error) {
    logAsyncError('下载公告附件失败', error)
  }
}

watch(
  () => [props.modelValue, props.noticeId],
  ([open]) => {
    if (open) {
      load()
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog v-model="visible" title="公告详情" width="620px" append-to-body class="notice-detail">
    <div v-loading="loading" class="notice-detail__body">
      <template v-if="detail">
        <h3 class="notice-detail__title">
          <el-tag v-if="detail.topFlag === 1" size="small" type="danger" effect="dark" class="notice-detail__top">置顶</el-tag>
          {{ detail.noticeTitle }}
        </h3>

        <div class="notice-detail__meta">
          <el-tag size="small" effect="plain" :type="typeTag">{{ typeLabel }}</el-tag>
          <el-tag size="small" effect="plain" :type="levelTag">{{ levelLabel }}</el-tag>
          <span class="notice-detail__meta-item">
            <AppIcon name="User" :size="13" />{{ detail.publisherName || '-' }}
          </span>
          <span class="notice-detail__meta-item">
            <AppIcon name="Clock" :size="13" />{{ formatTime(detail.publishTime) }}
          </span>
          <span class="notice-detail__meta-item">阅读 {{ detail.viewCount ?? 0 }} 次</span>
        </div>

        <div class="notice-detail__content">{{ detail.content || '（正文为空）' }}</div>

        <div v-if="detail.attachments?.length" class="notice-detail__attachments">
          <div class="notice-detail__attachments-title">
            <AppIcon name="Download" :size="14" />
            附件（{{ detail.attachments.length }}）
          </div>
          <button
            v-for="file in detail.attachments"
            :key="file.id"
            type="button"
            class="notice-detail__file"
            :title="`下载 ${file.fileName}`"
            @click="downloadAttachment(file)"
          >
            <AppIcon name="FileText" :size="15" class="notice-detail__file-icon" />
            <span class="notice-detail__file-name">{{ file.fileName }}</span>
            <span class="notice-detail__file-size">{{ formatSize(file.fileSize) }}</span>
            <span class="notice-detail__file-action">下载</span>
          </button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="公告不存在或已下线" :image-size="72" />
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.notice-detail__body {
  min-height: 160px;
}

.notice-detail__title {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  color: #1f2a37;
}

.notice-detail__top {
  flex-shrink: 0;
  margin-top: 4px;
}

.notice-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  padding-bottom: 12px;
  font-size: 13px;
  color: #8a94a5;
  border-bottom: 1px solid #eef1f6;
}

.notice-detail__meta-item {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

/* 正文纯文本：保留作者输入的换行与连续空格，不解析 HTML */
.notice-detail__content {
  padding: 14px 0;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.notice-detail__attachments {
  padding-top: 12px;
  border-top: 1px dashed #e8ecf3;
}

.notice-detail__attachments-title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #5b6474;
}

/* 每个附件是一条可点整行：文件名可能很长，命中区域不要只落在「下载」两个字上 */
.notice-detail__file {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 6px;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  background-color: #f7f9fc;
  border: 1px solid transparent;
  border-radius: 8px;
  transition: background-color 0.18s, border-color 0.18s;
}

.notice-detail__file:hover {
  background-color: #eef4ff;
  border-color: #cfe0ff;
}

.notice-detail__file:focus-visible {
  outline: 2px solid rgba(64, 158, 255, 0.45);
  outline-offset: 1px;
}

.notice-detail__file-icon {
  flex-shrink: 0;
  color: #7d8899;
}

.notice-detail__file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-detail__file-size {
  flex-shrink: 0;
  font-size: 12px;
  color: #a1a9b6;
}

.notice-detail__file-action {
  flex-shrink: 0;
  font-weight: 500;
  color: var(--oa-brand, #409eff);
}
</style>
