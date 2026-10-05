<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cleanLoginLogApi, exportLoginLogApi, getLoginLogPageApi } from '@/api/log'
import { getUserPageApi } from '@/api/user'
import { logAsyncError } from '@/utils/asyncGuard'
import type { LoginLogPageQuery, LoginLogVO, UserVO } from '@/api/types'

/** 清理时可选的历史区间（保留最近 N 天） */
const RETAIN_DAY_OPTIONS = [30, 90, 180, 365]

const loading = ref(false)
const exporting = ref(false)
const list = ref<LoginLogVO[]>([])
const total = ref(0)

/** el-date-picker 的区间值，查询时拆成 startTime / endTime */
const timeRange = ref<[string, string] | null>(null)

const query = reactive<LoginLogPageQuery>({
  pageNo: 1,
  pageSize: 10,
  username: undefined,
  loginStatus: undefined,
  ipAddress: undefined,
})

/**
 * 「用户」精确筛选的可选项。
 *
 * <p>只有持有 {@code system:user:query} 的人才能拉到用户列表（需该接口权限）；
 * 拿不到时降级为「只能用账号模糊查询」，页面不报错、不空白。</p>
 */
const userOptions = ref<UserVO[]>([])
const userOptionsLoaded = ref(false)

/** 是否展示「用户」下拉：仅超管 / 人事等有用户查询权限的角色 */
const canPickUser = computed(() => userOptions.value.length > 0)

const cleanVisible = ref(false)
/** 全量清理：true = 清除全部历史登录日志（后端仍保留本次操作的审计日志） */
const cleanAll = ref(false)
const cleanRetainDays = ref(90)
const cleaning = ref(false)

async function loadList() {
  loading.value = true
  try {
    const page = await getLoginLogPageApi({
      ...query,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
    })
    list.value = page.records
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadList()
}

function handleReset() {
  query.userId = undefined
  query.username = undefined
  query.loginStatus = undefined
  query.ipAddress = undefined
  timeRange.value = null
  handleSearch()
}

async function handleExport() {
  exporting.value = true
  try {
    await exportLoginLogApi({
      ...query,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
    })
    ElMessage.success('导出已开始，请留意浏览器下载')
  } finally {
    exporting.value = false
  }
}

function openCleanDialog() {
  cleanRetainDays.value = 90
  cleanAll.value = false
  cleanVisible.value = true
}

/** 清理截止时间 = 当前时间往前推 N 天，即保留最近 N 天 */
function cleanBefore(): string {
  return formatDateTime(new Date(Date.now() - cleanRetainDays.value * 24 * 60 * 60 * 1000))
}

function formatDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

async function handleClean() {
  const all = cleanAll.value
  const before = cleanBefore()
  const tip = all
    ? '将清空全部历史登录日志（含归档表中的历史副本）。追溯链将不可恢复。'
    : `将删除 ${before} 之前的所有登录日志，且无法恢复。`
  try {
    // 危险等级最高：全量清理要求连续两次确认
    await ElMessageBox.confirm(`${tip}\n\n此操作不可撤销，且会在审计日志中留痕。`, `清理${cn}日志`,
      { type: 'warning', confirmButtonText: all ? '我了解风险，继续' : '确定清理' })
    if (all) {
      await ElMessageBox.confirm(
        `最后确认：真的要清空全部${cn}日志吗？该动作会销毁所有历史追溯凭据。`,
        '全量清理二次确认',
        { type: 'error', confirmButtonText: '确认清空', cancelButtonText: '我再想想' },
      )
    }
  } catch {
    return
  }
  cleaning.value = true
  try {
    const deleted = await cleanLoginLogApi(all ? undefined : before, all)
    ElMessage.success(`已清理 ${deleted} 条登录日志`)
    cleanVisible.value = false
    handleSearch()
  } finally {
    cleaning.value = false
  }
}

/** 尝试载入用户下拉；无权限或失败都静默降级，不影响日志查询主流程 */
async function loadUserOptions() {
  try {
    const page = await getUserPageApi({ pageNo: 1, pageSize: 200 })
    userOptions.value = page.records ?? []
  } catch (error) {
    logAsyncError('加载用户下拉选项失败', error)
  } finally {
    userOptionsLoaded.value = true
  }
}

onMounted(() => {
  loadList()
  loadUserOptions()
})
</script>

<template>
  <div class="log-page">
    <el-card shadow="never">
      <el-form :inline="true" class="log-page__filters">
        <el-form-item v-if="canPickUser" label="用户">
          <el-select v-model="query.userId" placeholder="全部用户" clearable filterable class="log-page__select">
            <el-option v-for="u in userOptions" :key="u.id" :label="`${u.realName}（${u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录账号">
          <el-input
            v-model="query.username"
            placeholder="支持模糊匹配"
            clearable
            class="log-page__input"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="登录状态">
          <el-select v-model="query.loginStatus" placeholder="全部" clearable class="log-page__select">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="IP 地址">
          <el-input
            v-model="query.ipAddress"
            placeholder="支持模糊匹配"
            clearable
            class="log-page__input"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="登录时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            class="log-page__range"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="log-page__toolbar">
        <el-button v-permission="'system:login-log:export'" :loading="exporting" @click="handleExport">
          导出 Excel
        </el-button>
        <el-button v-permission="'system:login-log:clean'" type="danger" plain @click="openCleanDialog">
          清理日志
        </el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="登录账号" min-width="120" />
        <el-table-column label="登录类型" width="110">
          <template #default="{ row }">
            {{ row.loginType === 1 ? '账号密码登录' : '其他' }}
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP 地址" min-width="130" />
        <el-table-column prop="location" label="登录地点" min-width="110">
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column prop="browser" label="浏览器" min-width="110">
          <template #default="{ row }">{{ row.browser || '-' }}</template>
        </el-table-column>
        <el-table-column prop="os" label="操作系统" min-width="110">
          <template #default="{ row }">{{ row.os || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.loginStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.loginStatus === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.failReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="登录时间" width="170" />
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="log-page__pagination"
        @current-change="loadList"
        @size-change="handleSearch"
      />
    </el-card>

    <el-dialog v-model="cleanVisible" title="清理登录日志" width="460px">
      <el-alert
        type="warning"
        :closable="false"
        title="清理后不可恢复，请确认保留区间与合规要求一致。"
      />
      <el-form label-width="110px" class="log-page__clean-form">
        <el-form-item label="清理范围">
          <el-radio-group v-model="cleanAll">
            <el-radio :value="false">按时间</el-radio>
            <el-radio :value="true">全量（危险）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!cleanAll" label="保留最近">
          <el-select v-model="cleanRetainDays">
            <el-option v-for="day in RETAIN_DAY_OPTIONS" :key="day" :label="`${day} 天`" :value="day" />
          </el-select>
        </el-form-item>
        <el-form-item label="将删除">
          <span v-if="cleanAll" class="log-page__clean-danger">
            全部历史日志（本次操作的审计日志仍会保留）
          </span>
          <span v-else class="log-page__clean-tip">{{ cleanBefore() }} 之前的日志</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cleanVisible = false">取消</el-button>
        <el-button type="danger" :loading="cleaning" @click="handleClean">确定清理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.log-page__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}

.log-page__input {
  width: 170px;
}

.log-page__select {
  width: 130px;
}

.log-page__range {
  width: 380px;
}

.log-page__toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.log-page__pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.log-page__clean-form {
  margin-top: 16px;
}

.log-page__clean-tip {
  color: #f56c6c;
  font-weight: 600;
}
 .log-page__clean-danger {
  color: var(--el-color-danger);
  font-weight: 500;
}
</style>
