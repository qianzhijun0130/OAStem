<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RECRUIT_OFFER_STATUS_OPTIONS, type RecruitOfferVO } from '@/api/recruit'
import OfferFormDialog from '../dialogs/OfferFormDialog.vue'
import ConvertDialog from '../dialogs/ConvertDialog.vue'
import { useRecruitOffer } from '../composables/useRecruitOffer'
import type { RecruitOptions } from '../composables/useRecruitOptions'

/**
 * 「Offer」标签页（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>行内四个动作：发出（待发出态才可用）、接受、拒绝（要填理由）、转正为员工。
 * 转正会建/更新 `oa_user`，是这条流水线的终点。</p>
 */
const props = defineProps<{ options: RecruitOptions }>()

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
  send,
  accept,
  refuse,
} = useRecruitOffer()

const formRef = ref<InstanceType<typeof OfferFormDialog> | null>(null)
const convertRef = ref<InstanceType<typeof ConvertDialog> | null>(null)

onMounted(load)

async function openCreate() {
  // 候选人下拉只放终试（stage=4），所以必须先有候选人全量
  await Promise.all([props.options.ensureCandidates(), props.options.ensurePosts()])
  formRef.value?.openCreate()
}

async function openConvert(row: RecruitOfferVO) {
  await props.options.ensureUsers()
  convertRef.value?.open(row)
}

defineExpose({ refresh })
</script>

<template>
  <div class="recruit__head">
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="o in RECRUIT_OFFER_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-action="search" @click="search">查询</el-button>
        <el-button data-action="reset" @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="recruit__toolbar">
      <el-button v-permission="'hr:recruit:offer'" type="primary" data-action="create" @click="openCreate">
        发起 Offer
      </el-button>
    </div>
  </div>

  <el-table v-loading="loading" :data="list" border>
    <el-table-column prop="offerNo" label="Offer 编号" width="160" />
    <el-table-column prop="candidateName" label="候选人" width="100" />
    <el-table-column prop="positionName" label="岗位" min-width="120" show-overflow-tooltip />
    <el-table-column label="薪资" width="100">
      <template #default="{ row }">{{ row.salary != null ? row.salary : '-' }}</template>
    </el-table-column>
    <el-table-column prop="reportDate" label="报到日期" width="120" />
    <el-table-column prop="sendWayLabel" label="发送方式" width="100" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag :type="row.status === 3 ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="280" fixed="right" header-align="center" align="center">
      <template #default="{ row }">
        <el-button
          v-permission="'hr:recruit:offer'"
          icon-only
          link
          type="warning"
          title="发出"
          data-action="send"
          :disabled="row.status !== 1"
          @click="send(row)"
        >
          发出
        </el-button>
        <el-button icon-only link type="success" title="接受" data-action="accept" @click="accept(row)">接受</el-button>
        <el-button icon-only link type="danger" title="拒绝" data-action="refuse" @click="refuse(row)">拒绝</el-button>
        <el-button
          v-permission="'hr:recruit:offer'"
          icon-only
          link
          type="primary"
          title="转正为员工"
          data-action="convert"
          @click="openConvert(row)"
        >
          转正
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

  <OfferFormDialog ref="formRef" :options="options" @saved="refresh" />
  <ConvertDialog ref="convertRef" :options="options" @saved="refresh" />
</template>
