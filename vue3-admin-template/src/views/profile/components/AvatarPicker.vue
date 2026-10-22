<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { updateAvatarApi } from '@/api/profile'
import { uploadFileApi } from '@/api/file'
import { BUILTIN_AVATARS, isBuiltinAvatar } from '@/utils/avatars'
import AppIcon from '@/components/icons/AppIcon.vue'

/**
 * 更换头像弹窗：内置头像直接写地址，自传头像先走上传接口拿地址再写。
 *
 * <p>为什么把「上传 + 保存」都放在弹窗里：文件对象只在弹窗内存在，
 * 交给父组件反而要额外传 File / 上传态，职责更碎；弹窗对外只暴露 {@code success(url)}。</p>
 */
const props = defineProps<{
  modelValue: boolean
  /** 当前头像地址，用于回显选中态 */
  currentAvatar?: string
  /** 无头像时头像位显示的文字 */
  fallbackText?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [url: string]
}>()

/** 与后端 file.allowed-suffixes 的图片子集保持一致 */
const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp'
const MAX_MB = 2

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const tab = ref<'builtin' | 'upload'>('builtin')
const selected = ref('')
const pendingFile = ref<File | null>(null)
const localPreview = ref('')
const saving = ref(false)

/** 预览优先显示「将要保存的那张」，用户点确定前就能看到结果 */
const previewUrl = computed(() => {
  if (tab.value === 'upload') {
    return localPreview.value || props.currentAvatar || ''
  }
  return selected.value || props.currentAvatar || ''
})

const currentIsBuiltin = computed(() => isBuiltinAvatar(props.currentAvatar))

/** 每次打开都按当前头像重置，避免上次取消后残留选择 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    selected.value = currentIsBuiltin.value ? (props.currentAvatar ?? '') : ''
    tab.value = currentIsBuiltin.value || !props.currentAvatar ? 'builtin' : 'upload'
    clearPending()
  },
)

function clearPending() {
  pendingFile.value = null
  if (localPreview.value) {
    URL.revokeObjectURL(localPreview.value)
    localPreview.value = ''
  }
}

function onFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) {
    return
  }
  if (!ACCEPT.split(',').includes(raw.type)) {
    ElMessage.warning('仅支持 JPG / PNG / GIF / WebP 格式的图片')
    return
  }
  if (raw.size > MAX_MB * 1024 * 1024) {
    ElMessage.warning(`头像大小不能超过 ${MAX_MB}MB`)
    return
  }
  clearPending()
  pendingFile.value = raw
  localPreview.value = URL.createObjectURL(raw)
}

async function handleConfirm() {
  if (tab.value === 'builtin') {
    if (!selected.value) {
      ElMessage.warning('请先选择一个系统头像')
      return
    }
    await save(selected.value)
    return
  }
  if (!pendingFile.value) {
    ElMessage.warning('请先选择要上传的图片')
    return
  }
  saving.value = true
  try {
    const uploaded = await uploadFileApi(pendingFile.value)
    await save(uploaded.url)
  } finally {
    saving.value = false
  }
}

async function save(url: string) {
  saving.value = true
  try {
    await updateAvatarApi(url)
    ElMessage.success('头像已更新')
    emit('success', url)
    visible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="更换头像" width="560px" append-to-body>
    <div class="avatar-picker__preview">
      <el-avatar :size="68" :src="previewUrl" class="avatar-picker__avatar">
        {{ (fallbackText || '?').charAt(0) }}
      </el-avatar>
      <div class="avatar-picker__hint">
        <p class="avatar-picker__hint-title">当前选择</p>
        <p class="avatar-picker__hint-desc">
          {{ tab === 'builtin' ? '选择一张系统头像，免上传、即点即用' : '上传自己的照片，建议使用正方形图片' }}
        </p>
      </div>
    </div>

    <el-tabs v-model="tab" class="avatar-picker__tabs">
      <el-tab-pane label="系统头像" name="builtin">
        <div class="avatar-grid">
          <button
            v-for="item in BUILTIN_AVATARS"
            :key="item.id"
            type="button"
            class="avatar-grid__item"
            :class="{ 'is-active': item.url === selected }"
            :title="item.name"
            :aria-label="item.name"
            @click="selected = item.url"
          >
            <img :src="item.url" :alt="item.name" loading="lazy" />
            <span v-if="item.url === selected" class="avatar-grid__check">
              <AppIcon name="Check" :size="12" />
            </span>
          </button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="上传头像" name="upload">
        <el-upload
          class="avatar-upload"
          drag
          :show-file-list="false"
          :auto-upload="false"
          :accept="ACCEPT"
          :on-change="onFileChange"
        >
          <AppIcon name="ImagePlus" :size="26" class="avatar-upload__icon" />
          <div class="avatar-upload__text">将图片拖到此处，或<em>点击选择</em></div>
        </el-upload>
        <p v-if="pendingFile" class="avatar-upload__file">已选择：{{ pendingFile.name }}</p>
        <p v-else class="avatar-upload__tip">支持 JPG / PNG / GIF / WebP，单张不超过 2MB</p>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.avatar-picker__preview {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px 14px;
  margin-bottom: 6px;
  background-color: #f7f9fc;
  border-radius: 10px;
}

.avatar-picker__avatar {
  flex-shrink: 0;
  box-shadow: 0 0 0 2px #fff, 0 0 0 3.5px rgba(64, 158, 255, 0.18);
}

.avatar-picker__hint-title {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2a37;
}

.avatar-picker__hint-desc {
  margin: 0;
  font-size: 12px;
  color: #8a94a5;
}

/* 6 列 2 行：12 个内置头像一屏放完，不出现滚动条 */
.avatar-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  padding: 4px 2px 2px;
}

.avatar-grid__item {
  position: relative;
  padding: 0;
  cursor: pointer;
  background: none;
  border: 2px solid transparent;
  border-radius: 50%;
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}

.avatar-grid__item img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 50%;
}

.avatar-grid__item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px rgba(23, 43, 77, 0.16);
}

.avatar-grid__item:focus-visible {
  outline: 2px solid rgba(64, 158, 255, 0.5);
  outline-offset: 2px;
}

.avatar-grid__item.is-active {
  border-color: var(--oa-brand, #409eff);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.18);
}

/* 选中角标：右下角小白勾，避免只靠描边区分（描边在小尺寸下不明显） */
.avatar-grid__check {
  position: absolute;
  right: -1px;
  bottom: -1px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: #fff;
  background-color: var(--oa-brand, #409eff);
  border: 2px solid #fff;
  border-radius: 50%;
}

.avatar-upload__icon {
  margin-bottom: 6px;
  color: #a4adba;
}

.avatar-upload__text {
  font-size: 13px;
  color: #5b6474;
}

.avatar-upload__text em {
  font-style: normal;
  color: var(--oa-brand, #409eff);
}

.avatar-upload__file,
.avatar-upload__tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #8a94a5;
}

.avatar-upload__file {
  color: #2f7d4f;
}
</style>
