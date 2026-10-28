<script setup lang="ts">
import { onMounted, ref } from 'vue'
import SheetTab from './tabs/SheetTab.vue'
import CycleTab from './tabs/CycleTab.vue'
import DistTab from './tabs/DistTab.vue'
import PlanTab from './tabs/PlanTab.vue'
import IndicatorTab from './tabs/IndicatorTab.vue'
import { usePerfCycleBoard } from './composables/usePerfCycleBoard'
import { usePerfOptions } from './composables/usePerfOptions'
import './perf.css'

/**
 * 绩效管理：五个标签页的容器。
 *
 * <p>FE-OPT-06 拆分完成后的形态——本文件只做三件事：页签切换、创建两份共享状态
 * （周期看板、下拉选项）、以及把「周期变了」这件事转达给需要重查的两个页签。</p>
 *
 * <p><b>跨页签依赖有三条</b>（原实现混在 1367 行里，拆完必须显式化）：</p>
 * <ol>
 *   <li>周期被「考核表 / 考核周期 / 等级分布」三处共用 → 单实例看板，页面持有；</li>
 *   <li>推进/归档/补齐/删除周期会改变每张考核表的可操作状态 → 周期页签发 `changed`，
 *       页面让「考核表」「等级分布」重查；</li>
 *   <li>新建方案要出现在周期表单、新建指标要出现在方案表单 → 由共享的 options 实例
 *       在保存后 `force` 刷新，无需页面中转。</li>
 * </ol>
 */
const activeTab = ref('sheet')

/** 周期看板：三个页签共享 */
const cycleBoard = usePerfCycleBoard()
/** 方案/指标下拉：两个弹窗共享，首次用到才取 */
const options = usePerfOptions()

const sheetRef = ref<InstanceType<typeof SheetTab> | null>(null)
const distRef = ref<InstanceType<typeof DistTab> | null>(null)

/** 周期发生增删改后，依赖它的两个页签同步重查 */
function onCycleChanged() {
  void sheetRef.value?.refresh()
  void distRef.value?.refresh()
}

// 周期是首屏就要用的（考核表默认按当前周期筛选），这里先热起来；
// 子页签也会 await 一次幂等加载，谁先到谁触发，不会重复请求
onMounted(() => {
  void cycleBoard.ensureLoaded()
})
</script>

<template>
  <div class="perf">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="考核表" name="sheet" lazy>
          <SheetTab ref="sheetRef" :cycle-board="cycleBoard" />
        </el-tab-pane>
        <el-tab-pane label="考核周期" name="cycle" lazy>
          <CycleTab :cycle-board="cycleBoard" :options="options" @changed="onCycleChanged" />
        </el-tab-pane>
        <el-tab-pane label="等级分布" name="dist" lazy>
          <DistTab ref="distRef" :cycle-board="cycleBoard" />
        </el-tab-pane>
        <el-tab-pane label="绩效方案" name="plan" lazy>
          <PlanTab :options="options" />
        </el-tab-pane>
        <el-tab-pane label="指标库" name="indicator" lazy>
          <IndicatorTab :options="options" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
