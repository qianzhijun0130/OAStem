<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/icons/AppIcon.vue'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ toggle: [] }>()

const userStore = useUserStore()
const router = useRouter()

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await userStore.logout()
}

function goProfile() {
  router.push('/profile')
}
</script>

<template>
  <div class="oa-navbar">
    <div class="oa-navbar__left">
      <el-icon class="oa-navbar__toggle" :size="20" @click="emit('toggle')">
        <AppIcon :name="collapsed ? 'PanelLeftOpen' : 'PanelLeftClose'" :size="20" />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="userStore.displayName">{{ userStore.displayName }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="oa-navbar__right">
      <el-dropdown trigger="click">
        <span class="oa-navbar__user">
          <el-avatar :size="28" :src="userStore.userInfo?.avatar">
            {{ userStore.displayName.charAt(0) }}
          </el-avatar>
          <span class="oa-navbar__name">{{ userStore.displayName }}</span>
          <el-tag v-if="userStore.userInfo?.deptName" size="small" type="info">
            {{ userStore.userInfo.deptName }}
          </el-tag>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goProfile">个人中心</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.oa-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 16px;
}

.oa-navbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.oa-navbar__toggle {
  cursor: pointer;
  color: #303133;
}

.oa-navbar__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.oa-navbar__name {
  font-size: 14px;
  color: #303133;
}
</style>
