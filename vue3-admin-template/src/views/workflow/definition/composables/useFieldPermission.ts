import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getNodeFieldPermissionsApi, saveNodeFieldPermissionsApi } from '@/api/form'
import { logAsyncError } from '@/utils/asyncGuard'
import type { NodeFieldPermissionItem, NodeFormBindRow } from '@/api/types'

/**
 * 节点字段权限配置（FE-OPT-05 从 index.vue 抽出）。
 *
 * <p>字段权限依附于「节点↔表单」关系：只有**已绑定表单**的节点才可配置，
 * 因此候选节点直接取绑定列表。保存是整节点全量覆盖，成功后回读一次让
 * `configured` 标记与后端一致。</p>
 */
export interface FieldPermissionDeps {
  /** 当前编辑的模板 id */
  editingId: Ref<number | null>
  /** 节点绑定列表（由 useNodeForms 提供） */
  binds: Ref<NodeFormBindRow[]>
  /** 按 nodeKey 取绑定项（由 useNodeForms 提供，避免两处各写一份查找逻辑） */
  bindOf: (nodeKey: string) => NodeFormBindRow | null
}

export function useFieldPermission(deps: FieldPermissionDeps) {
  const { editingId, binds, bindOf } = deps

  /* ---------------- 字段权限配置 ---------------- */
  const permNodeKey = ref('')
  const permFields = ref<NodeFieldPermissionItem[]>([])
  const savingPerm = ref(false)

  /**
   * 可配置字段权限的节点列表。
   *
   * <p>只列<b>已绑定表单</b>的节点：字段权限依附于「节点↔表单」关系，
   * 未绑定时配置无处生效，让用户先到「表单绑定」标签页绑定。</p>
   */
  const permNodes = computed(() =>
    binds.value.map((b) => ({ nodeKey: b.nodeKey, nodeName: b.nodeName })),
  )

  /** 当前选中节点的绑定信息 */
  const currentPermBind = computed(() => (permNodeKey.value ? bindOf(permNodeKey.value) : null))

  /** 切换节点：载入该节点的字段权限清单 */
  async function onPermNodeChange(nodeKey: string) {
    permFields.value = []
    const bind = bindOf(nodeKey)
    if (!editingId.value || !bind) {
      return
    }
    try {
      const vo = await getNodeFieldPermissionsApi(editingId.value, nodeKey, bind.formId)
      permFields.value = vo.fields ?? []
    } catch (error) {
      logAsyncError('加载节点字段权限失败', error)
    }
  }

  /** 保存当前节点的字段权限（整节点全量覆盖） */
  async function saveFieldPermissions() {
    const bind = currentPermBind.value
    if (!editingId.value || !bind || !permFields.value.length) {
      return
    }
    savingPerm.value = true
    try {
      await saveNodeFieldPermissionsApi({
        flowDefId: editingId.value,
        nodeKey: permNodeKey.value,
        nodeName: bind.nodeName,
        formId: bind.formId,
        fields: permFields.value.map((f) => ({
          prop: f.prop,
          fieldLabel: f.label,
          permission: f.permission,
        })),
      })
      ElMessage.success('字段权限已保存')
      // 回读一次，让 configured 标记与后端一致
      await onPermNodeChange(permNodeKey.value)
    } catch (error) {
      logAsyncError('保存字段权限失败', error)
    } finally {
      savingPerm.value = false
    }
  }

  return {
    permNodeKey,
    permFields,
    savingPerm,
    permNodes,
    currentPermBind,
    onPermNodeChange,
    saveFieldPermissions,
  }
}
