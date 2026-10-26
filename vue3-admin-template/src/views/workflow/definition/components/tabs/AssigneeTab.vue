<script setup lang="ts">
import type { DeptVO, NodeConfigItem, RoleOptionVO, UserVO } from '@/api/types'
import {
  ASSIGNEE_APPLICANT,
  ASSIGNEE_DEPT_LEADER,
  ASSIGNEE_LEADER,
  ASSIGNEE_ROLE,
  ASSIGNEE_TYPE_OPTIONS,
  ASSIGNEE_USER,
} from '../../assigneeConstants'

/**
 * 设计器「审批人配置」标签页（FE-OPT-05 拆分自 index.vue）。
 *
 * <p><b>本页签直接改传入节点的字段</b>（`row.assigneeType` / `assigneeValue` / `timeoutHours` /
 * `timeoutAction`）：`nodes` 是父页面持有的同一份节点配置数组，表格里的 `row` 就是该数组的元素，
 * 改它等于改父级状态——这与拆分前行为完全一致，保存时父级的同步逻辑照常读取。</p>
 *
 * <p>审批人规则常量来自 `assigneeConstants.ts`，与父级保存/发布前校验共用同一份定义。</p>
 */
defineProps<{
  /** 画布同步过来的节点清单（表格行即数组元素，编辑直接落在这份数据上） */
  nodes: NodeConfigItem[]
  userOptions: UserVO[]
  roleOptions: RoleOptionVO[]
  /** 部门树（el-tree-select 需要 children 结构） */
  deptOptions: DeptVO[]
}>()
</script>

<template>
  <el-alert
    type="info"
    :closable="false"
    show-icon
    title="审批人是待办归属的唯一依据"
    description="未配置的节点在流程发起后无人可办，保存与发布时都会校验。「超时(小时)」留空则用全局默认（48 小时），填 0 表示该节点不计超时；「到期动作」在到期后由系统每分钟执行一次（仅标记 / 提醒 / 自动驳回）。"
    class="designer__alert"
  />
  <el-table :data="nodes" size="small" border max-height="420">
    <el-table-column label="节点" min-width="110">
      <template #default="{ row }">
        <div>{{ row.nodeName }}</div>
        <el-tag size="small" :type="row.nodeKind === 'createTask' ? 'warning' : 'primary'">
          {{ row.nodeKind === 'createTask' ? '创建任务' : '用户任务' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="规则" width="120">
      <template #default="{ row }">
        <el-select v-model="row.assigneeType" size="small">
          <el-option
            v-for="opt in ASSIGNEE_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>
    </el-table-column>
    <el-table-column label="审批人" min-width="130">
      <template #default="{ row }">
        <el-select
          v-if="row.assigneeType === ASSIGNEE_USER"
          v-model="row.assigneeValue"
          size="small"
          filterable
          placeholder="选择人员"
        >
          <el-option
            v-for="u in userOptions"
            :key="u.id"
            :label="u.realName"
            :value="String(u.id)"
          />
        </el-select>
        <el-select
          v-else-if="row.assigneeType === ASSIGNEE_ROLE"
          v-model="row.assigneeValue"
          size="small"
          placeholder="选择角色"
        >
          <el-option
            v-for="r in roleOptions"
            :key="r.id"
            :label="r.roleName"
            :value="String(r.id)"
          />
        </el-select>
        <el-select
          v-else-if="row.assigneeType === ASSIGNEE_LEADER"
          v-model="row.assigneeValue"
          size="small"
          placeholder="直属主管（默认）"
          style="width: 100%"
        >
          <el-option label="第 1 级 · 直属主管" value="1" />
          <el-option label="第 2 级 · 上级主管" value="2" />
          <el-option label="第 3 级 · 再上一级" value="3" />
        </el-select>
        <el-tree-select
          v-else-if="row.assigneeType === ASSIGNEE_DEPT_LEADER"
          :model-value="row.assigneeValue ? Number(row.assigneeValue) : undefined"
          :data="deptOptions"
          :props="{ label: 'deptName', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          filterable
          size="small"
          placeholder="选择部门"
          style="width: 100%"
          @update:model-value="(v: number | null) => (row.assigneeValue = v == null ? '' : String(v))"
        />
        <span v-else-if="row.assigneeType === ASSIGNEE_APPLICANT" class="designer__muted">
          申请人本人审批（会出现在发起人的待办中）
        </span>
        <span v-else class="designer__warn">未配置审批人</span>
      </template>
    </el-table-column>
    <el-table-column label="超时(小时)" width="120">
      <template #default="{ row }">
        <!-- 留空 = 用全局默认；0 = 该节点不计超时；后端最终会 clamp 到 0~8760 -->
        <el-input-number
          v-model="row.timeoutHours"
          size="small"
          :min="0"
          :max="8760"
          :controls="false"
          placeholder="默认"
          style="width: 100%"
        />
      </template>
    </el-table-column>
    <el-table-column label="到期动作" width="120">
      <template #default="{ row }">
        <!-- 默认 0 仅标记：不改状态、只写一条超时记录，避免误伤业务 -->
        <el-select v-model="row.timeoutAction" size="small" placeholder="仅标记">
          <el-option :value="0" label="仅标记" />
          <el-option :value="1" label="提醒" />
          <el-option :value="2" label="自动驳回" />
        </el-select>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="请先绘制任务节点并同步" :image-size="50" />
    </template>
  </el-table>
</template>
