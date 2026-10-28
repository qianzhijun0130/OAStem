import { reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkinEnrollApi, listEnrollsApi, type TrainEnrollVO } from '@/api/training'
import { logAsyncError } from '@/utils/asyncGuard'

/**
 * 报名/成绩看板：选定期次后的花名册（FE-OPT-06 从 index.vue 抽出）。
 *
 * <p><b>为什么单独抽、且在页面里只实例化一次</b>：「报名管理」与「成绩管理」两个
 * 页签看的是<b>同一份</b>期次花名册（列不同而已）。原实现共用 `enrollSessionId` +
 * `enrollList`，若拆成两个 tab 各自持状态，选一次期次要选两遍，回切还会丢数据。
 * 所以本 composable 由页面创建、以 props 传给两个 tab。</p>
 *
 * <p>状态用 `reactive` 而非多个 ref：子组件拿到的是 `board = enroll.state`，
 * 模板里 `v-model="board.sessionId"` 能直接写回（如果是 ref 集合，
 * 从 props 解构出来的 ref 不会被编译器识别成 ref，v-model 会写坏）。</p>
 */
export function useTrainEnroll() {
  const state = reactive({
    /** 当前选中的期次；为空时两个页签都是空表 */
    sessionId: undefined as number | undefined,
    list: [] as TrainEnrollVO[],
    loading: false,
  })

  async function load() {
    if (state.sessionId == null) {
      state.list = []
      return
    }
    state.loading = true
    try {
      state.list = (await listEnrollsApi(state.sessionId)) || []
    } catch (error) {
      logAsyncError('listEnrolls', error)
    } finally {
      state.loading = false
    }
  }

  // 换期次即重取；两个页签共用，任一处切换两边同步
  watch(() => state.sessionId, () => void load())

  /** 签到：窗口期与状态校验都在后端，前端只做二次确认 */
  async function checkin(row: TrainEnrollVO) {
    try {
      await ElMessageBox.confirm(`确认为「${row.userName}」签到吗？`, '培训签到', {
        type: 'warning',
        confirmButtonText: '签到',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await checkinEnrollApi(row.id)
      ElMessage.success('已签到')
      await load()
    } catch (error) {
      logAsyncError('checkin', error)
    }
  }

  return { state, load, checkin }
}

/** 看板实例类型：页面创建、两个页签以 props 接收 */
export type TrainEnrollBoard = ReturnType<typeof useTrainEnroll>
