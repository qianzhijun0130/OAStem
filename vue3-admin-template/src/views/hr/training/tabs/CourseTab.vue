<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { TRAIN_REQUIRED_OPTIONS, TRAIN_TYPE_OPTIONS, type TrainCourseVO } from '@/api/training'
import CourseFormDialog from '../dialogs/CourseFormDialog.vue'
import { useTrainCourse } from '../composables/useTrainCourse'
import type { UserVO } from '@/api/types'

/**
 * 「培训课程」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>自包含：列表、筛选、新增/编辑弹窗、删除。只从页面接一份员工候选
 * （内部讲师下拉），避免每个弹窗各拉一次。</p>
 */
defineProps<{
  userCandidates: UserVO[]
}>()

const { list, loading, query, total, pageNo, pageSize, load, search, reset, refresh, handleSizeChange, handlePageChange, remove } =
  useTrainCourse()

// 列表由本组件自己加载（页签懒渲染，首次切到这里才触发）
onMounted(load)

const formRef = ref<InstanceType<typeof CourseFormDialog> | null>(null)

function openCreate() {
  formRef.value?.openCreate()
}

function openEdit(row: TrainCourseVO) {
  formRef.value?.openEdit(row)
}
</script>

<template>
  <div class="training__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="课程编码 / 名称"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="o in TRAIN_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="必修">
        <el-select v-model="query.required" clearable placeholder="全部" style="width: 110px">
          <el-option v-for="o in TRAIN_REQUIRED_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="training__toolbar">
      <el-button v-permission="'hr:training:course'" type="primary" data-action="create" @click="openCreate">
        新增课程
      </el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="courseCode" label="编码" width="130" />
    <el-table-column prop="courseName" label="课程名称" min-width="140" show-overflow-tooltip />
    <el-table-column prop="trainTypeLabel" label="类型" width="110" />
    <el-table-column label="讲师" min-width="120">
      <template #default="{ row }">
        {{ row.teacherType === 2 ? row.teacherName + (row.teacherOrg ? `（${row.teacherOrg}）` : '') : row.teacherName || '-' }}
      </template>
    </el-table-column>
    <el-table-column prop="hours" label="学时" width="80" />
    <el-table-column prop="requiredLabel" label="必修" width="80" />
    <el-table-column prop="sessionCount" label="开课" width="70" />
    <el-table-column prop="completedCount" label="结业" width="70" />
    <el-table-column label="状态" width="80">
      <template #default="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="160" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:training:course'"
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
          v-permission="'hr:training:course'"
          icon-only
          link
          type="danger"
          title="删除"
          data-action="delete"
          @click="remove(row)"
        >
          删除
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

  <CourseFormDialog ref="formRef" :user-candidates="userCandidates" @saved="refresh" />
</template>
