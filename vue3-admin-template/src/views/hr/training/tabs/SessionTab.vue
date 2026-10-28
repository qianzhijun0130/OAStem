<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  TRAIN_SESSION_STATUS_OPTIONS,
  type TrainCourseVO,
  type TrainSessionVO,
} from '@/api/training'
import SessionFormDialog from '../dialogs/SessionFormDialog.vue'
import { useTrainSession } from '../composables/useTrainSession'

/**
 * 「培训期次」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：列表、筛选、新增/编辑弹窗、取消期次。课程下拉由页面统一提供。</p>
 */
defineProps<{
  courseOptions: TrainCourseVO[]
}>()

const {
  list,
  loading,
  query,
  total,
  pageNo,
  pageSize,
  load,
  search,
  reset,
  refresh,
  handleSizeChange,
  handlePageChange,
  cancelSession,
} = useTrainSession()

onMounted(load)

const formRef = ref<InstanceType<typeof SessionFormDialog> | null>(null)

function openCreate() {
  formRef.value?.openCreate()
}

function openEdit(row: TrainSessionVO) {
  formRef.value?.openEdit(row)
}
</script>

<template>
  <div class="training__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="期次名称" clearable style="width: 200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="课程">
        <el-select v-model="query.courseId" clearable filterable placeholder="全部" style="width: 180px">
          <el-option v-for="c in courseOptions" :key="c.id" :label="c.courseName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="o in TRAIN_SESSION_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="training__toolbar">
      <el-button v-permission="'hr:training:session'" type="primary" data-action="create" @click="openCreate">
        新增期次
      </el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
    <el-table-column prop="sessionName" label="期次" min-width="130" show-overflow-tooltip />
    <el-table-column prop="startTime" label="开始" width="160" />
    <el-table-column prop="endTime" label="结束" width="160" />
    <el-table-column prop="location" label="地点" width="100" show-overflow-tooltip />
    <el-table-column label="名额" width="110">
      <template #default="{ row }">
        {{ row.enrolledCount }}/{{ row.capacity && row.capacity > 0 ? row.capacity : '不限' }}
        <span v-if="row.waitCount" class="training__wait">候补{{ row.waitCount }}</span>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : row.status === 4 ? 'info' : 'warning'" effect="plain">
          {{ row.statusLabel }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="160" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:training:session'"
          icon-only
          link
          type="warning"
          title="编辑"
          data-action="edit"
          @click="openEdit(row)"
        >
          编辑
        </el-button>
        <el-button
          v-permission="'hr:training:session'"
          icon-only
          link
          type="danger"
          title="取消期次"
          data-action="cancel"
          :disabled="row.status === 4"
          @click="cancelSession(row)"
        >
          取消
        </el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    class="training__pagination"
    :current-page="pageNo"
    :page-size="pageSize"
    :page-sizes="[10, 20, 50]"
    :total="total"
    layout="total, sizes, prev, pager, next, jumper"
    background
    @size-change="handleSizeChange"
    @current-change="handlePageChange"
  />

  <SessionFormDialog ref="formRef" :course-options="courseOptions" @saved="refresh" />
</template>
