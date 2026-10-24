<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelEnrollApi,
  checkinEnrollApi,
  enrollSessionApi,
  getMyAvailableApi,
  getMyTrainPageApi,
  TRAIN_ENROLL_STATUS_OPTIONS,
  type TrainEnrollVO,
  type TrainSessionVO,
} from '@/api/training'
import { useListQuery } from '@/composables/useListQuery'
import { logAsyncError } from '@/utils/asyncGuard'

const activeTab = ref('mine')

/* ============================ 我的培训 ============================ */
const myQ = useListQuery<TrainEnrollVO, { status?: number }>({
  initialQuery: () => ({ status: undefined }),
  fetcher: (p) => getMyTrainPageApi(p),
})

/* ============================ 可报名 ============================ */
const availableLoading = ref(false)
const availableList = ref<TrainSessionVO[]>([])

async function loadAvailable() {
  availableLoading.value = true
  try {
    availableList.value = (await getMyAvailableApi()) || []
  } catch (error) {
    logAsyncError('loadAvailable', error)
  } finally {
    availableLoading.value = false
  }
}

async function enroll(row: TrainSessionVO) {
  try {
    await ElMessageBox.confirm(`确定报名「${row.courseName} · ${row.sessionName}」吗？`, '报名培训', {
      type: 'warning',
      confirmButtonText: '报名',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await enrollSessionApi(row.id)
    ElMessage.success(row.remainQuota === -1 || (row.remainQuota ?? 0) > 0 ? '报名成功' : '已加入候补')
    await loadAvailable()
    if (activeTab.value === 'mine') {
      await myQ.refresh()
    }
  } catch (error) {
    logAsyncError('enroll', error)
  }
}

/* ============================ 我的培训：取消 / 签到 ============================ */
async function cancelMine(row: TrainEnrollVO) {
  try {
    await ElMessageBox.confirm('确定取消该培训报名吗？（开课前 24 小时内取消将记为爽约）', '取消报名', {
      type: 'warning',
      confirmButtonText: '取消报名',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await cancelEnrollApi(row.id)
    ElMessage.success('已取消报名')
    await myQ.refresh()
  } catch (error) {
    logAsyncError('cancelEnroll', error)
  }
}

async function checkinMine(row: TrainEnrollVO) {
  try {
    await checkinEnrollApi(row.id)
    ElMessage.success('签到成功')
    await myQ.refresh()
  } catch (error) {
    logAsyncError('checkin', error)
  }
}

onMounted(() => {
  void myQ.load()
  void loadAvailable()
})
</script>

<template>
  <div class="my-training">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 我的培训 -->
        <el-tab-pane label="我的培训" name="mine">
          <div class="my-training__head">
            <el-form :inline="true" @submit.prevent>
              <el-form-item label="状态">
                <el-select v-model="myQ.query.status" clearable placeholder="全部" style="width: 140px">
                  <el-option v-for="o in TRAIN_ENROLL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="myQ.search">查询</el-button>
                <el-button @click="myQ.reset">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
          <el-table v-loading="myQ.loading.value" :data="myQ.list.value" border>
            <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
            <el-table-column prop="sessionName" label="期次" min-width="130" show-overflow-tooltip />
            <el-table-column prop="enrollWayLabel" label="报名方式" width="100" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 6 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="checkinTime" label="签到时间" width="160" />
            <el-table-column label="成绩" width="80">
              <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  icon-only
                  link
                  type="success"
                  title="签到"
                  :disabled="row.status !== 1 && row.status !== 2"
                  @click="checkinMine(row)"
                >
                  签到
                </el-button>
                <el-button
                  icon-only
                  link
                  type="danger"
                  title="取消报名"
                  :disabled="row.status === 3 || row.status === 4 || row.status === 6 || row.status === 7"
                  @click="cancelMine(row)"
                >
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="my-training__pagination"
            :current-page="myQ.pageNo.value"
            :page-size="myQ.pageSize.value"
            :page-sizes="[10, 20, 50]"
            :total="myQ.total.value"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="myQ.handleSizeChange"
            @current-change="myQ.handlePageChange"
          />
        </el-tab-pane>

        <!-- 可报名 -->
        <el-tab-pane label="可报名" name="available">
          <el-table v-loading="availableLoading" :data="availableList" border>
            <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
            <el-table-column prop="sessionName" label="期次" min-width="130" show-overflow-tooltip />
            <el-table-column prop="startTime" label="开始" width="160" />
            <el-table-column prop="endTime" label="结束" width="160" />
            <el-table-column prop="location" label="地点" width="100" show-overflow-tooltip />
            <el-table-column label="名额" width="120">
              <template #default="{ row }">
                <span v-if="row.remainQuota === -1">不限</span>
                <span v-else>
                  剩 {{ row.remainQuota }} / {{ row.capacity && row.capacity > 0 ? row.capacity : 0 }}
                  <span v-if="row.waitCount" class="my-training__wait">候补{{ row.waitCount }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right" header-align="center" align="center">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  :disabled="!row.enrollable"
                  @click="enroll(row)"
                >
                  {{ row.remainQuota === -1 || (row.remainQuota ?? 0) > 0 ? '报名' : '候补' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.my-training__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px;
}
.my-training__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.my-training__wait {
  margin-left: 6px;
  color: var(--el-color-warning);
  font-size: 12px;
}
</style>
