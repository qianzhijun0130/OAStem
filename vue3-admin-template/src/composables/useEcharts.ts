import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

/**
 * ECharts 按需引入。
 *
 * 不用 `import * as echarts from 'echarts'`（整包约 1MB）：工作台只用折线图与饼图，
 * 按需注册能把图表相关产物压到几十 KB。新增图表类型时记得在这里补注册。
 */
echarts.use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

type EChartsInstance = ReturnType<typeof echarts.init>

/**
 * 图表渲染组合式函数：负责实例的懒创建、重绘与销毁。
 *
 * <p>容器尺寸为 0 时不初始化——元素尚未挂载或被 v-if 隐藏时 init 会得到 0×0 画布，
 * 表现为「图表不显示」。调用方应在有数据后再调用 render。</p>
 */
export function useEcharts(container: Ref<HTMLElement | undefined>) {
  let instance: EChartsInstance | null = null

  function render(option: EChartsCoreOption) {
    const el = container.value
    if (!el) {
      return
    }
    if (!instance) {
      instance = echarts.init(el)
    }
    // notMerge = true：数据从「有」变「无」时，残留的旧 series 会被清掉
    instance.setOption(option, true)
  }

  /** 侧边栏折叠、窗口缩放后容器宽度变化，需要手动触发重排 */
  function resize() {
    instance?.resize()
  }

  onMounted(() => window.addEventListener('resize', resize))
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    instance?.dispose()
    instance = null
  })

  return { render, resize }
}
