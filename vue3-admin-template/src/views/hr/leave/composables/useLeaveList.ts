import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelLeaveApi, exportLeaveApi, getLeavePageApi, returnLeaveApi } from '@/api/hr'
import { logAsyncError } from '@/utils/asyncGuard'
import type { LeaveVO } from '@/api/types'

/**
 * 「请假单」tab 的列表、导出与撤销/销假（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>撤销与销假用 ElMessageBox 而不另做弹窗：确认框只有一个日期字段，
 * 多一层自绘弹窗反而比系统原生输入框更慢。这两段逻辑不碰模板 ref，
 * 因此可以安全地放在 composable（弹窗表单里的 ref 则必须留在组件内）。</p>
 */
export function useLeaveList() {
  const query = reactive({ month: '', leaveStatus: undefined as number | undefined, keyword: '' })
  const list = ref<LeaveVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const page = reactive({ pageNo: 1, pageSize: 10 })
  const exporting = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await getLeavePageApi({ ...query, ...page })
      list.value = res?.records || []
      total.value = res?.total || 0
    } catch (error) {
      logAsyncError('loadLeaves', error)
    } finally {
      loading.value = false
    }
  }

  /** 导出：后端直接返回 xlsx 流，按当前查询条件下载（不翻页、不带分页参数） */
  async function exportList() {
    exporting.value = true
    try {
      await exportLeaveApi({
        month: query.month || undefined,
        leaveStatus: query.leaveStatus,
        keyword: query.keyword || undefined,
      })
      ElMessage.success('导出完成')
    } catch (error) {
      logAsyncError('exportLeave', error)
    } finally {
      exporting.value = false
    }
  }

  /** 销假入口只在「已通过 + 未销假」时出现；重复销假会让额度退两次，后端也会拦 */
  function canReturn(row: LeaveVO) {
    return row.leaveStatus === 2 && (row.cancelStatus ?? 0) === 0
  }

  /** 返回值表示「是否真的改了数据」：撤销/销假都会动额度，tab 需要据此通知兄弟 tab 刷新 */
  async function cancel(row: LeaveVO): Promise<boolean> {
    try {
      await ElMessageBox.confirm(`确定撤销单据「${row.billCode || row.id}」吗？额度会立即释放。`, '撤销请假', {
        type: 'warning',
        confirmButtonText: '确定撤销',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
    try {
      await cancelLeaveApi(row.id)
      ElMessage.success('已撤销')
      await load()
      return true
    } catch (error) {
      logAsyncError('cancelLeave', error)
      return false
    }
  }

  /** 销假（提前返岗）：登记实际返岗日期，差额额度自动退回 */
  async function returnFromLeave(row: LeaveVO): Promise<boolean> {
    const startDate = (row.startTime || '').slice(0, 10)
    const endDate = (row.endTime || '').slice(0, 10)
    let returnDate: string
    try {
      const result = await ElMessageBox.prompt(
        `登记实际返岗日期（须晚于 ${startDate}，不晚于 ${endDate} 次日）。差额额度会自动退回。`,
        '销假',
        {
          type: 'warning',
          confirmButtonText: '确定销假',
          cancelButtonText: '取消',
          inputType: 'date',
          inputValue: endDate,
          inputValidator: (value: string) => {
            if (!value) {
              return '请选择返岗日期'
            }
            if (value <= startDate) {
              return `返岗日期须晚于 ${startDate}`
            }
            return true
          },
        },
      )
      returnDate = result.value
    } catch {
      return false
    }
    try {
      await returnLeaveApi(row.id, returnDate)
      ElMessage.success('已销假，差额额度已退回')
      await load()
      return true
    } catch (error) {
      logAsyncError('returnFromLeave', error)
      return false
    }
  }

  return { query, list, total, loading, page, exporting, load, exportList, canReturn, cancel, returnFromLeave }
}
