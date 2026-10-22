<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePasswordApi, getProfileApi, updateProfileApi } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/icons/AppIcon.vue'
import AvatarPicker from './components/AvatarPicker.vue'
import type { PasswordChangeRequest, ProfileUpdateRequest, ProfileVO } from '@/api/types'

const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('basic')
const profile = ref<ProfileVO | null>(null)

const infoFormRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()

/** 业务上已取消性别「未知」，默认男（1） */
const DEFAULT_GENDER = 1

const infoForm = reactive<ProfileUpdateRequest>({
  nickname: '',
  gender: DEFAULT_GENDER,
  mobile: '',
  email: '',
})

const pwdForm = reactive<PasswordChangeRequest & { confirmPassword: string }>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const infoRules: FormRules = {
  mobile: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

/** 两次输入一致性校验交给前端，避免为一个纯交互问题往返一次后端 */
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const avatarPickerVisible = ref(false)
const savingInfo = ref(false)
const savingPwd = ref(false)

const displayAvatar = computed(() => profile.value?.avatar || '')
/** 无头像时头像位显示的名字首字 */
const avatarText = computed(() => (profile.value?.realName || profile.value?.username || '?').charAt(0))

/** 弹窗里存好之后同步本地状态：顶栏头像读的是 store，不改会等到下次刷新才变 */
function onAvatarUpdated(url: string) {
  if (profile.value) {
    profile.value.avatar = url
  }
  if (userStore.userInfo) {
    userStore.userInfo.avatar = url
  }
}

async function loadProfile() {
  loading.value = true
  try {
    const data = await getProfileApi()
    profile.value = data
    infoForm.nickname = data.nickname ?? ''
    // 存量数据可能残留历史「未知(0)」，这里按男回显，用户保存后即被纠正
    infoForm.gender = data.gender === 2 ? 2 : DEFAULT_GENDER
    infoForm.mobile = data.mobile ?? ''
    infoForm.email = data.email ?? ''
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function submitInfo() {
  const valid = await infoFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  savingInfo.value = true
  try {
    await updateProfileApi({ ...infoForm })
    await loadProfile()
    // 同步顶栏昵称，否则改完还要刷新页面才生效
    if (userStore.userInfo) {
      userStore.userInfo.nickname = infoForm.nickname ?? ''
    }
    ElMessage.success('资料已保存')
  } finally {
    savingInfo.value = false
  }
}

async function submitPassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  savingPwd.value = true
  try {
    await changePasswordApi({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码修改成功')
    pwdFormRef.value?.resetFields()
  } finally {
    savingPwd.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div v-loading="loading" class="profile">
    <el-card shadow="never" class="profile__summary">
      <div class="summary">
        <div class="summary__avatar">
          <button
            type="button"
            class="summary__avatar-btn"
            title="更换头像"
            aria-label="更换头像"
            @click="avatarPickerVisible = true"
          >
            <el-avatar :size="88" :src="displayAvatar" class="summary__avatar-img">
              {{ avatarText }}
            </el-avatar>
            <span class="summary__avatar-mask">
              <AppIcon name="Camera" :size="16" />
              <span>更换</span>
            </span>
          </button>
          <div class="summary__avatar-tip">点击更换头像</div>
        </div>

        <div class="summary__info">
          <div class="summary__name">
            {{ profile?.realName }}
            <el-tag v-for="role in profile?.roles ?? []" :key="role" size="small" type="info">
              {{ role }}
            </el-tag>
          </div>
          <el-descriptions :column="2" border size="small" class="summary__desc">
            <el-descriptions-item label="登录账号">{{ profile?.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ profile?.workNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ profile?.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="岗位">{{ profile?.position || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入职时间">{{ profile?.entryDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">
              {{ profile?.lastLoginTime || '-' }}
              <span v-if="profile?.lastLoginIp" class="summary__ip">（{{ profile.lastLoginIp }}）</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本资料" name="basic">
          <el-form ref="infoFormRef" :model="infoForm" :rules="infoRules" label-width="90px" class="form">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="infoForm.nickname" placeholder="请输入昵称" maxlength="50" />
            </el-form-item>
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="infoForm.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="infoForm.mobile" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="infoForm.email" placeholder="请输入邮箱" maxlength="100" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingInfo" @click="submitInfo">保存</el-button>
            </el-form-item>
            <div class="form__tip">
              姓名、工号、部门、岗位、入职时间属于人事档案，如需变更请联系管理员。
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password">
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px" class="form">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPwd" @click="submitPassword">确认修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <AvatarPicker
      v-model="avatarPickerVisible"
      :current-avatar="displayAvatar"
      :fallback-text="avatarText"
      @success="onAvatarUpdated"
    />
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.summary__avatar {
  flex-shrink: 0;
  text-align: center;
}

/* 头像位本身是按钮：hover 才浮出「更换」蒙层，避免常驻的相机图标干扰 */
.summary__avatar-btn {
  position: relative;
  display: inline-block;
  padding: 0;
  cursor: pointer;
  background: none;
  border: none;
  border-radius: 50%;
}

.summary__avatar-img {
  display: block;
  box-shadow: 0 0 0 3px #fff, 0 0 0 5px rgba(64, 158, 255, 0.16);
  transition: box-shadow 0.18s;
}

.summary__avatar-btn:hover .summary__avatar-img {
  box-shadow: 0 0 0 3px #fff, 0 0 0 5px rgba(64, 158, 255, 0.4);
}

.summary__avatar-btn:focus-visible {
  outline: 2px solid rgba(64, 158, 255, 0.55);
  outline-offset: 4px;
}

.summary__avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background-color: rgba(15, 26, 41, 0.48);
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.18s;
}

.summary__avatar-btn:hover .summary__avatar-mask,
.summary__avatar-btn:focus-visible .summary__avatar-mask {
  opacity: 1;
}

.summary__avatar-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.summary__info {
  flex: 1;
  min-width: 0;
}

.summary__name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.summary__desc {
  max-width: 720px;
}

.summary__ip {
  color: #909399;
}

.form {
  max-width: 460px;
}

.form__tip {
  padding-left: 90px;
  font-size: 12px;
  color: #909399;
}
</style>
