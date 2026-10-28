<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RECRUIT_POST_STATUS_OPTIONS, type RecruitPostVO } from '@/api/recruit'
import PostFormDialog from '../dialogs/PostFormDialog.vue'
import { useRecruitPost } from '../composables/useRecruitPost'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 「招聘职位」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>新增/删除职位会改变「可投递职位」集合（候选人、面试、Offer 三个弹窗都用），
 * 所以成功后发 `changed` 让页面刷共享职位下拉。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

const emit = defineEmits<{ changed: [] }>()

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
  remove,
} = useRecruitPost()

const formRef = ref<InstanceType<typeof PostFormDialog> | null>(null)

onMounted(load)

async function openCreate() {
  await Promise.all([props.options.ensureDemands(), props.options.ensureUsers()])
  formRef.value?.openCreate()
}

async function openEdit(row: RecruitPostVO) {
  await Promise.all([props.options.ensureDemands(), props.options.ensureUsers()])
  formRef.value?.openEdit(row)
}

function onSaved() {
  void refresh()
  emit('changed')
}

async function onRemove(row: RecruitPostVO) {
  if (await remove(row)) {
    emit('changed')
  }
}

defineExpose({ refresh })
</script>

<template>
  <div class="recruit__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="职位名称" clearable style="width: 200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="o in RECRUIT_POST_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="recruit__toolbar">
      <el-button v-permission="'hr:recruit:post'" type="primary" data-action="create" @click="openCreate">新增职位</el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="demandCode" label="关联需求" width="150" show-overflow-tooltip />
    <el-table-column prop="postName" label="职位名称" min-width="140" show-overflow-tooltip />
    <el-table-column prop="city" label="工作地点" width="100" />
    <el-table-column label="薪资范围" width="160">
      <template #default="{ row }">
        {{ row.salaryMin != null ? row.salaryMin : '?' }} ~ {{ row.salaryMax != null ? row.salaryMax : '?' }}
      </template>
    </el-table-column>
    <el-table-column prop="headCount" label="人数" width="70" />
    <el-table-column prop="channelLabel" label="渠道" width="100" />
    <el-table-column prop="hrName" label="负责HR" width="100" />
    <el-table-column prop="candidateCount" label="投递" width="70" />
    <el-table-column prop="hiredCount" label="入职" width="70" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag :type="row.status === 2 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="160" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:recruit:post'"
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
          v-permission="'hr:recruit:post'"
          icon-only
          link
          type="danger"
          title="删除"
          data-action="delete"
          @click="onRemove(row)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    class="recruit__pagination"
    :current-page="pageNo"
    :page-size="pageSize"
    :page-sizes="[10, 20, 50]"
    :total="total"
    layout="total, sizes, prev, pager, next, jumper"
    background
    @size-change="handleSizeChange"
    @current-change="handlePageChange"
  />

  <PostFormDialog ref="formRef" :options="options" @saved="onSaved" />
</template>
