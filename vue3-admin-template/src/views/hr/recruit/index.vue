<script setup lang="ts">
import { ref } from 'vue'
import DemandTab from './tabs/DemandTab.vue'
import PostTab from './tabs/PostTab.vue'
import CandidateTab from './tabs/CandidateTab.vue'
import InterviewTab from './tabs/InterviewTab.vue'
import OfferTab from './tabs/OfferTab.vue'
import BoardTab from './tabs/BoardTab.vue'
import { useRecruitOptions } from './composables/useRecruitOptions'
import './recruit.css'

/**
 * 招聘管理：六个标签页的容器。
 *
 * <p>FE-OPT-06 拆分完成后的形态——本文件只做三件事：页签切换、创建共享下拉、
 * 把「上游数据变了」转达给下游下拉。</p>
 *
 * <p><b>这条流水线的跨页签依赖</b>（原实现里全部隐式，后果是下拉常显示旧数据）：</p>
 * <pre>
 *   需求 ──→ 职位弹窗的「关联需求」
 *   职位 ──→ 候选人 / 面试 / Offer 弹窗的「职位」
 *   候选人 ──→ 面试弹窗的「候选人」、Offer 弹窗的「终试候选人」
 * </pre>
 * <p>三份下拉由 `useRecruitOptions()` 单实例持有；上游页签发 `changed`，
 * 页面 `force` 刷新对应那一份，下游弹窗下次打开即为最新。</p>
 */
const activeTab = ref('demand')

/** 六份共享下拉：谁用谁取（首屏只取部门树） */
const options = useRecruitOptions()

/** 需求变更 → 刷新「关联需求」下拉 */
function onDemandChanged() {
  void options.ensureDemands(true)
}
/** 职位变更 → 刷新「职位」下拉 */
function onPostChanged() {
  void options.ensurePosts(true)
}
/** 候选人变更（新增/阶段流转）→ 刷新「候选人」下拉（含 Offer 的终试过滤） */
function onCandidateChanged() {
  void options.ensureCandidates(true)
}
</script>

<template>
  <div class="recruit">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="招聘需求" name="demand" lazy>
          <DemandTab :options="options" @changed="onDemandChanged" />
        </el-tab-pane>
        <el-tab-pane label="招聘职位" name="post" lazy>
          <PostTab :options="options" @changed="onPostChanged" />
        </el-tab-pane>
        <el-tab-pane label="候选人" name="candidate" lazy>
          <CandidateTab :options="options" @changed="onCandidateChanged" />
        </el-tab-pane>
        <el-tab-pane label="面试" name="interview" lazy>
          <InterviewTab :options="options" />
        </el-tab-pane>
        <el-tab-pane label="Offer" name="offer" lazy>
          <OfferTab :options="options" />
        </el-tab-pane>
        <el-tab-pane label="招聘看板" name="board" lazy>
          <BoardTab />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
