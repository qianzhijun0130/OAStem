import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bindNodeFormApi, getNodeFormsApi, unbindNodeFormApi } from '@/api/form'
import { logAsyncError } from '@/utils/asyncGuard'
import type { NodeConfigItem, NodeFormBindRow } from '@/api/types'

/**
 * 节点↔表单绑定（FE-OPT-05 从 index.vue 抽出）。
 *
 * <p>绑定关系以 nodeKey 为键，与后端 `oa_flow_node_form` 的约定一致；发起环节（init）
 * 恒在最前并作为唯一主表单候选。后端绑定接口是**全量覆盖**语义，重绑即替换。</p>
 */

/** 发起环节的节点键，与后端 /primary-form 的约定一致 */
const INIT_NODE_KEY = 'init'

/** 可选表单（已发布/启用的表单定义） */
export interface FormOptionRow {
  id: number
  formCode: string
  formName: string
}

export interface NodeFormsDeps {
  /** 当前编辑的模板 id（新建时为 null，此时不允许绑定） */
  editingId: Ref<number | null>
  /** 画布同步过来的任务节点（用于拼候选列表与回填节点名） */
  nodeConfig: Ref<NodeConfigItem[]>
  /** 可选表单，由页面统一加载后传入 */
  formOptions: Ref<FormOptionRow[]>
}

export function useNodeForms(deps: NodeFormsDeps) {
  const { editingId, nodeConfig, formOptions } = deps

  /** 节点↔表单绑定（node_form_binds）：节点键 → 绑定项 */
  const nodeFormBinds = ref<NodeFormBindRow[]>([])

  /**
   * 表单绑定下拉的候选节点。
   *
   * <p>发起环节（init）恒在最前并作为主表单候选；其余为画布上的任务节点。
   * 绑定关系以 nodeKey 为键，与后端 {@code oa_flow_node_form} 的约定一致。</p>
   */
  const bindableNodes = computed(() => {
    const rest = nodeConfig.value.map((n) => ({ nodeKey: n.nodeId, nodeName: n.nodeName }))
    return [{ nodeKey: INIT_NODE_KEY, nodeName: '发起申请（init）' }, ...rest]
  })

  function bindOf(nodeKey: string) {
    return nodeFormBinds.value.find((b) => b.nodeKey === nodeKey) ?? null
  }

  /** 加载某流程模板的节点表单绑定 */
  async function loadNodeForms(flowDefId: number) {
    try {
      const list = await getNodeFormsApi(flowDefId)
      nodeFormBinds.value = list.map((b) => ({
        id: b.id ?? 0,
        nodeKey: b.nodeKey,
        nodeName: b.nodeName ?? b.nodeKey,
        formId: b.formId,
        formName: b.formName ?? '',
        formCode: b.formCode ?? '',
        isPrimary: b.isPrimary ?? 0,
      }))
    } catch (error) {
      logAsyncError('加载节点表单绑定失败', error)
    }
  }

  /** 绑定/重绑某节点的表单：后端为全量覆盖语义，重绑即替换 */
  async function onBindForm(nodeKey: string, formId: number | null) {
    if (!editingId.value) {
      ElMessage.warning('请先保存流程模板，再绑定节点表单')
      return
    }
    const existing = bindOf(nodeKey)
    try {
      if (!formId) {
        if (existing?.id) {
          await unbindNodeFormApi(existing.id)
        }
        nodeFormBinds.value = nodeFormBinds.value.filter((b) => b.nodeKey !== nodeKey)
        return
      }
      const target = formOptions.value.find((f) => f.id === formId)
      // 发起环节默认作为主表单；中间节点一律非主表单（同一流程只允许一个主表单）
      const isPrimary = nodeKey === INIT_NODE_KEY ? 1 : 0
      await bindNodeFormApi({
        flowDefId: editingId.value,
        nodeKey,
        nodeName: bindableNodes.value.find((n) => n.nodeKey === nodeKey)?.nodeName ?? nodeKey,
        formId,
        isPrimary,
      })
      await loadNodeForms(editingId.value)
      ElMessage.success(target ? `已绑定表单「${target.formName}」` : '已绑定表单')
    } catch (error) {
      logAsyncError('绑定节点表单失败', error)
      // 回滚下拉选中值，避免 UI 显示与后端不一致
      await loadNodeForms(editingId.value)
    }
  }

  return { nodeFormBinds, bindableNodes, bindOf, loadNodeForms, onBindForm }
}
