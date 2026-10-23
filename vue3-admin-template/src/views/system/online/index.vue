<script setup lang="ts">
import { h, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOnlineUserPageApi, kickOnlineUserApi } from '@/api/online'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'
import ListPage from '@/components/list/ListPage.vue'
import type { OnlineUserVO } from '@/api/types'

/**
 * 在线用户。
 *
 * <p>会话数据在 Redis（登录时写入、TTL = 令牌有效期），所以这一页天然是「快照」：
 * 用户关掉浏览器不会立刻消失，要等令牌过期；管理员踢人后列表会马上少一行。
 * 这一点在页面上明确写出来，避免被当成「实时在线状态」误读。</p>
 *
 * <p>踢出后该用户「踢出时刻之前签发」的令牌全部失效，重新登录即可继续使用；</p>
 */
interface OnlineQuery {
  keyword?: string
}

const kickingId = ref<number>()

const { list, total, loading, query, pageNo, pageSize, search, reset,
        handleSizeChange, handlePageChange } =
  useListQuery<OnlineUserVO, OnlineQuery>({
    initialQuery: () => ({ keyword: undefined }),
    fetcher: (params) => getOnlineUserPageApi(params),
    onError: (error) => logAsyncError('加载在线用户失败', error),
  })

/** 已在线时长：后端只给分钟数，这里按「分钟 / 小时 / 天」分档展示，避免出现 1440 分钟这种数字 */
function durationText(minutes: number): string {
  if (!minutes || minutes < 1) {
    return '不到 1 分钟'
  }
  if (minutes < 60) {
    return `${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时 ${minutes % 60} 分钟`
  }
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`
}

/** 终端摘要：UA 太长，只取浏览器与系统两个关键词，够区分即可 */
function terminalText(userAgent?: string): string {
  if (!userAgent) {
    return '-'
  }
  const browser = /Edg\//.test(userAgent) ? 'Edge'
    : /Chrome\//.test(userAgent) ? 'Chrome'
      : /Firefox\//.test(userAgent) ? 'Firefox'
        : /Safari\//.test(userAgent) ? 'Safari' : '其它'
  const os = /Windows/.test(userAgent) ? 'Windows'
    : /Mac OS X/.test(userAgent) ? 'macOS'
      : /Android/.test(userAgent) ? 'Android'
        : /iPhone|iPad/.test(userAgent) ? 'iOS' : '未知系统'
  return `${browser} / ${os}`
}

async function handleKick(row: OnlineUserVO) {
  // 强制下线会让对方正在填的表单、正在跑的请求全部失效，二次确认是必要的。
  // message 传 VNode 而不是拼 HTML 字符串：用户名来自数据库，拼字符串等于给 XSS 留口子。
  const message = h('div', null, [
    h('p', { style: 'margin:0 0 6px' }, [
      '确定让 ',
      h('b', null, row.realName || row.username),
      ' 立即下线吗？',
    ]),
    h('p', { style: 'margin:0;color:#8a94a5;font-size:12px' },
      '对方当前令牌会立刻失效，需重新登录；进行中的表单填写可能丢失。'),
  ])
  try {
    await ElMessageBox.confirm(message, '强制下线', {
      type: 'warning',
      confirmButtonText: '确定下线',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  kickingId.value = row.userId
  try {
    await kickOnlineUserApi(row.userId)
    ElMessage.success(`已强制下线：${row.realName || row.username}`)
    await search()
  } catch (error) {
    // 典型的「手慢一步」：对方已经自己登出，后端返回 1114，刷新列表即可
    logAsyncError('强制下线失败', error)
    await search()
  } finally {
    kickingId.value = undefined
  }
}

function handleReset() {
  reset()
}
</script>

<template>
  <ListPage
    :data="list"
    :total="total"
    :loading="loading"
    :page-no="pageNo"
    :page-size="pageSize"
    :page-sizes="[10, 20, 50]"
    pagination-layout="total, sizes, prev, pager, next"
    row-key="userId"
    empty-text="当前没有在线用户"
    @size-change="handleSizeChange"
    @current-change="handlePageChange"
  >
    <template #filters>
      <el-form :inline="true" class="online__filters">
        <el-form-item label="账号 / 姓名">
          <el-input
            v-model="query.keyword"
            placeholder="支持模糊匹配"
            clearable
            class="online__input"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="online__hint">
        数据来自登录会话快照（有效期与令牌一致）：用户关闭浏览器不会立刻消失，
        被强制下线后会立即从列表移除。
      </div>
    </template>

    <el-table-column prop="username" label="登录账号" min-width="120" show-overflow-tooltip />
    <el-table-column prop="realName" label="姓名" min-width="100" show-overflow-tooltip>
      <template #default="{ row }">{{ row.realName || '-' }}</template>
    </el-table-column>
    <el-table-column prop="clientIp" label="客户端 IP" width="130">
      <template #default="{ row }">{{ row.clientIp || '-' }}</template>
    </el-table-column>
    <el-table-column label="终端" width="150">
      <template #default="{ row }">
        <span :title="row.userAgent || ''">{{ terminalText(row.userAgent) }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="loginTime" label="登录时间" width="170">
      <template #default="{ row }">{{ row.loginTime || '-' }}</template>
    </el-table-column>
    <el-table-column label="已在线" width="140">
      <template #default="{ row }">{{ durationText(row.onlineMinutes) }}</template>
    </el-table-column>
    <el-table-column prop="expireTime" label="令牌到期" width="170">
      <template #default="{ row }">{{ row.expireTime || '-' }}</template>
    </el-table-column>
    <el-table-column label="操作" width="150" fixed="right">
      <template #default="{ row }">
        <el-button
          v-permission="'system:online:kick'"
          icon-only
          link
          type="danger"
          size="small"
          :loading="kickingId === row.userId"
          @click="handleKick(row)"
        >
          强制下线
        </el-button>
      </template>
    </el-table-column>
  </ListPage>
</template>

<style scoped>
.online__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}

.online__input {
  width: 180px;
}

.online__hint {
  margin: 4px 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #8a94a5;
}
</style>
