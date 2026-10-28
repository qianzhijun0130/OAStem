import { ref } from 'vue'
import { getBoardApi } from '@/api/recruit'
import { logAsyncError } from '@/utils/asyncGuard'
import type { RecruitBoardVO } from '@/api/recruit'

/** 「招聘看板」的汇总数据（FE-OPT-06 从 index.vue 抽出） */
export function useRecruitBoard() {
  const loading = ref(false)
  const board = ref<RecruitBoardVO | null>(null)

  async function load() {
    loading.value = true
    try {
      board.value = await getBoardApi()
    } catch (error) {
      logAsyncError('loadBoard', error)
    } finally {
      loading.value = false
    }
  }

  return { loading, board, load }
}
