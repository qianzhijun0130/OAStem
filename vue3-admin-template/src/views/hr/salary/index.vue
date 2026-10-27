<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDeptTreeApi } from '@/api/dept'
import { logAsyncError } from '@/utils/asyncGuard'
import RecordTab from './tabs/RecordTab.vue'
import ProfileTab from './tabs/ProfileTab.vue'
import PolicyTab from './tabs/PolicyTab.vue'

/**
 * 薪酬管理：三个标签页的容器。
 *
 * <p>FE-OPT-06 拆分完成后的形态——本文件只做三件事：承载标签页切换、
 * 加载部门树（核算与档案两个 tab 的筛选项共用同一份）、以及样式。
 * 每个 tab 自带列表状态、composable 与弹窗；弹窗保存后由 tab 自己重载列表。</p>
 */

/** 当前标签页 */
const activeTab = ref('record')

/** 部门树扁平选项：核算与档案两个 tab 共用，只在这里请求一次 */
const deptOptions = ref<{ value: number; label: string }[]>([])

async function loadDeptOptions() {
  try {
    const tree = await getDeptTreeApi()
    const flat: { value: number; label: string }[] = []
    const walk = (nodes: any[], depth = 0) => {
      for (const node of nodes || []) {
        flat.push({ value: node.id, label: `${'　'.repeat(depth)}${node.deptName}` })
        walk(node.children || [], depth + 1)
      }
    }
    walk(tree || [])
    deptOptions.value = flat
  } catch (error) {
    logAsyncError('loadDeptOptions', error)
  }
}

onMounted(loadDeptOptions)
</script>

<template>
  <div class="salary">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="月度核算" name="record">
          <RecordTab :dept-options="deptOptions" />
        </el-tab-pane>
        <el-tab-pane label="薪资档案" name="profile">
          <ProfileTab :dept-options="deptOptions" />
        </el-tab-pane>
        <el-tab-pane label="社保政策" name="policy">
          <PolicyTab />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style>
.salary__tip {
  margin-bottom: 12px;
}

.salary__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.salary__hint {
  font-size: 12px;
  color: #909399;
}

.salary__net {
  color: #f56c6c;
}

/* 折算系数：未折算时弱化显示，折算时用强调色让 HR 一眼看到「这个人当月不是整月」 */
.salary__proration {
  color: #e6a23c;
  font-weight: 600;
  cursor: help;
}

.salary__muted {
  color: var(--oa-text-tertiary, #697180);
}

/* 换单位标记：本年度在前单位有收入，个税需年度汇算合并 */
.salary__prior {
  margin-left: 6px;
  cursor: help;
}

.salary__pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.salary__items {
  margin-top: 12px;
}
</style>
