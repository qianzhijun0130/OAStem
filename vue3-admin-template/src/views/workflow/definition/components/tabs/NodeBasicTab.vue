<script setup lang="ts">
import { computed, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import AppIcon from '@/components/icons/AppIcon.vue'
import { LUCIDE_ICON_NAMES } from '@/utils/icons'
import type { DictDataVO, NodeConfigItem } from '@/api/types'

/**
 * 设计器「节点基本」标签页（FE-OPT-05 拆分自 index.vue）。
 *
 * <p><b>为什么用字段级 v-model 而不是接收整个 form 对象</b>：直接改 prop 上的字段
 * 属于 `vue/no-mutating-props`（数据流向不可追踪、未来加 watch 会踩坑）。
 * 这里把 6 个字段各自声明成 model，父级仍是同一个 reactive 对象，读写语义不变。</p>
 *
 * <p>表单校验由本组件持有（`el-form` 在这里），通过 `defineExpose({ validate })`
 * 把校验能力交给父页面的保存流程——这是本页签唯一与父级强耦合的点。</p>
 */

/** 状态常量与父级保持一致：0 草稿 / 1 已发布 */
const FLOW_STATUS_DRAFT = 0
const FLOW_STATUS_PUBLISHED = 1

defineProps<{
  rules: FormRules
  /** 已部署过的模板不允许改流程编码 */
  hasDeployment: boolean
  categoryOptions: DictDataVO[]
  /** 画布同步过来的节点清单（只读展示） */
  nodeConfig: NodeConfigItem[]
}>()

const flowCode = defineModel<string>('flowCode', { required: true })
const flowName = defineModel<string>('flowName', { required: true })
const category = defineModel<string | undefined>('category')
const icon = defineModel<string | undefined>('icon')
const status = defineModel<number | undefined>('status')
const remark = defineModel<string | undefined>('remark')

/** el-form 的校验取值源：只读汇总，写入仍走各字段 model */
const model = computed(() => ({
  flowCode: flowCode.value,
  flowName: flowName.value,
  category: category.value,
  icon: icon.value,
  status: status.value,
  remark: remark.value,
}))

const formRef = ref<FormInstance>()

async function validate(): Promise<boolean> {
  if (!formRef.value) {
    return false
  }
  return await formRef.value.validate().catch(() => false)
}

defineExpose({ validate })
</script>

<template>
  <el-form ref="formRef" :model="model" :rules="rules" label-width="90px">
    <el-form-item label="流程编码" prop="flowCode">
      <el-input
        v-model="flowCode"
        :disabled="hasDeployment"
        placeholder="如 leave_apply、expense_apply"
      />
      <div class="designer__muted">
        仅限字母、数字、下划线，保存后不可修改。流程图里的流程标识会自动对齐为该编码，无需在画布中另外设置。
      </div>
    </el-form-item>
    <el-form-item label="流程名称" prop="flowName">
      <el-input v-model="flowName" placeholder="如 请假流程" />
    </el-form-item>
    <el-form-item label="分类">
      <el-select v-model="category" placeholder="请选择分类" clearable style="width: 100%">
        <el-option
          v-for="item in categoryOptions"
          :key="item.id"
          :label="item.itemLabel"
          :value="item.itemValue"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="流程图标">
      <div class="designer__icon-picker">
        <el-select
          v-model="icon"
          filterable
          clearable
          placeholder="选择图标（可选）"
          style="width: 260px"
        >
          <el-option v-for="name in LUCIDE_ICON_NAMES" :key="name" :label="name" :value="name">
            <span class="designer__icon-option">
              <AppIcon :name="name" :size="14" />{{ name }}
            </span>
          </el-option>
        </el-select>
        <span class="designer__icon-preview">
          <AppIcon :name="icon || 'FileText'" :size="20" />
          <span class="designer__muted">发起页卡片图标</span>
        </span>
      </div>
    </el-form-item>
    <el-form-item label="状态">
      <el-radio-group v-model="status">
        <el-radio :value="FLOW_STATUS_DRAFT">草稿</el-radio>
        <el-radio :value="FLOW_STATUS_PUBLISHED">已发布</el-radio>
      </el-radio-group>
      <div class="designer__muted">保存后仍需在列表点「发布」才对外可见</div>
    </el-form-item>
    <el-form-item label="备注">
      <el-input v-model="remark" type="textarea" :rows="2" />
    </el-form-item>

    <el-divider content-position="left">已识别节点</el-divider>
    <el-table :data="nodeConfig" size="small" border max-height="260">
      <el-table-column label="节点" min-width="120">
        <template #default="{ row }">
          <div>{{ row.nodeName }}</div>
          <el-tag size="small" :type="row.nodeKind === 'createTask' ? 'warning' : 'primary'">
            {{ row.nodeKind === 'createTask' ? '创建任务' : '用户任务' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="nodeId" label="节点 ID" min-width="110" show-overflow-tooltip />
      <template #empty>
        <el-empty description="请先绘制任务节点并同步" :image-size="50" />
      </template>
    </el-table>
    <div class="designer__muted designer__muted--block">
      含用户任务与创建任务两类；两者都支持配置审批人。
    </div>
  </el-form>
</template>
