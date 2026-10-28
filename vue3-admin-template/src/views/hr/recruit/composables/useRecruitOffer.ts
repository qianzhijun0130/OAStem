import { ElMessage, ElMessageBox } from 'element-plus'
import { acceptOfferApi, getOfferPageApi, refuseOfferApi, sendOfferApi } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import { useListQuery } from '@/composables/useListQuery'
import type { RecruitOfferVO } from '@/api/recruit'

/**
 * 「Offer」页签的列表 + 发出/接受/拒绝（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p>三者都是行内动作、都要重载列表；发起 Offer 与转正为员工是两个弹窗，各自提交。</p>
 */
export function useRecruitOffer() {
  const q = useListQuery<RecruitOfferVO, { status?: number; candidateId?: number }>({
    initialQuery: () => ({ status: undefined, candidateId: undefined }),
    fetcher: (p) => getOfferPageApi(p),
    immediate: false,
  })

  async function send(row: RecruitOfferVO) {
    try {
      await ElMessageBox.confirm(`确定向「${row.candidateName}」发出 Offer 吗？`, '发出 Offer', {
        type: 'warning',
        confirmButtonText: '发出',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await sendOfferApi(row.id, row.sendWay ?? 1)
      ElMessage.success('已发出')
      await q.refresh()
    } catch (error) {
      logAsyncError('sendOffer', error)
    }
  }

  async function accept(row: RecruitOfferVO) {
    try {
      await acceptOfferApi(row.id)
      ElMessage.success('已接受')
      await q.refresh()
    } catch (error) {
      logAsyncError('acceptOffer', error)
    }
  }

  async function refuse(row: RecruitOfferVO) {
    const { value } = await ElMessageBox.prompt('请填写拒绝理由', '拒绝 Offer', {
      type: 'warning',
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      inputType: 'textarea',
    }).catch(() => ({ value: null }))
    if (value === null) {
      return
    }
    try {
      await refuseOfferApi(row.id, value || undefined)
      ElMessage.success('已拒绝')
      await q.refresh()
    } catch (error) {
      logAsyncError('refuseOffer', error)
    }
  }

  return { ...q, send, accept, refuse }
}
