<script setup lang="ts">
import { ref } from 'vue'
import QuotaTab from './tabs/QuotaTab.vue'
import LeaveTab from './tabs/LeaveTab.vue'
import BizTab from './tabs/BizTab.vue'
import PolicyTab from './tabs/PolicyTab.vue'
import { BIZ_TABS } from './constants'
import './leave.css'

/**
 * 请假管理：五个标签页的容器。
 *
 * <p>FE-OPT-06 拆分完成后的形态——本文件只负责标签页切换与样式，
 * 列表状态、弹窗、请求全部由各 tab 自持（各 tab 首次激活时自己取数）。
 * 唯一的跨 tab 协作：请假 tab 提交/撤销/销假会改变额度，用 `changed` 事件
 * 通知页面刷新「我的额度」（额度状态在兄弟组件内，无法直接调用）。</p>
 */

const activeTab = ref('quota')

const quotaRef = ref<InstanceType<typeof QuotaTab> | null>(null)

function refreshQuota() {
  void quotaRef.value?.refresh()
}
</script>

<template>
  <div class="hr-leave">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="我的额度" name="quota" lazy>
          <QuotaTab ref="quotaRef" />
        </el-tab-pane>
        <el-tab-pane label="请假单" name="leave" lazy>
          <LeaveTab @changed="refreshQuota" />
        </el-tab-pane>
        <el-tab-pane v-for="tab in BIZ_TABS" :key="tab.name" :label="tab.label" :name="tab.name" lazy>
          <BizTab :type="tab.type" :label="tab.label" />
        </el-tab-pane>
        <el-tab-pane label="假别政策" name="policy" lazy>
          <PolicyTab />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
