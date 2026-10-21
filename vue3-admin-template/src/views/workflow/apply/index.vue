<script setup lang="ts">
/**
 * 发起流程：左侧按分类导航，右侧按分类分区陈列流程卡片，点卡片进入填单页。
 *
 * <p><b>数据驱动</b>：分类来自 `flow_category` 字典，流程来自只读的 options 接口，
 * 二者一合并就得出「分区 + 卡片」。新增分类或流程时**不需要改本页任何代码**——
 * 加字典项、给模板选分类即可。</p>
 *
 * <p><b>组件化</b>：左侧导航 {@link ApplyCategoryNav}、流程卡片 {@link ApplyFlowCard}
 * 都是纯展示组件（只吃 props、只抛事件），页面本身只负责取数、锚点滚动与跳转。</p>
 *
 * <p><b>为什么不再在本页填表</b>：填单页需要「单据编号 / 申请日期 / 保存草稿」，
 * 与本页的浏览职责不同；拆成独立页面后，返回时列表滚动位置与分类状态都还在。</p>
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFlowOptionsApi } from '@/api/flow'
import { getDictDataByTypeApi } from '@/api/dict'
import { logAsyncError } from '@/utils/asyncGuard'
import ApplyCategoryNav from './components/ApplyCategoryNav.vue'
import ApplyFlowCard from './components/ApplyFlowCard.vue'
import type { DictDataVO, FlowOptionVO } from '@/api/types'

const router = useRouter()

const flowOptions = ref<FlowOptionVO[]>([])
const categoryList = ref<DictDataVO[]>([])
const activeCategory = ref('')

/** 分类编码在字典中已删除时用这个 key 兜底，避免流程「消失」 */
const UNCATEGORIZED = '__uncategorized__'

interface CategoryGroup {
  code: string
  label: string
  flows: FlowOptionVO[]
}

/** 按字典顺序分组，「未分类」置底；字典里已删除的分类编码也保留展示 */
const groupedFlows = computed<CategoryGroup[]>(() => {
  const groups = new Map<string, FlowOptionVO[]>()
  for (const f of flowOptions.value) {
    const key = f.category && f.category.trim() ? f.category : UNCATEGORIZED
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(f)
  }
  const result: CategoryGroup[] = []
  for (const c of categoryList.value) {
    const flows = groups.get(c.itemValue)
    if (flows && flows.length) {
      result.push({ code: c.itemValue, label: c.itemLabel, flows })
      groups.delete(c.itemValue)
    }
  }
  if (groups.has(UNCATEGORIZED)) {
    result.push({ code: UNCATEGORIZED, label: '未分类', flows: groups.get(UNCATEGORIZED)! })
    groups.delete(UNCATEGORIZED)
  }
  for (const [code, flows] of groups) {
    result.push({ code, label: code, flows })
  }
  return result
})

/** 导航项：与分区一一对应（多带一个 count 供角标展示） */
const navItems = computed(() =>
  groupedFlows.value.map((g) => ({ code: g.code, label: g.label, count: g.flows.length })),
)

async function loadFlows() {
  try {
    flowOptions.value = await getFlowOptionsApi()
  } catch (error) {
    logAsyncError('加载可发起流程模板失败', error)
  }
}

async function loadCategories() {
  try {
    categoryList.value = await getDictDataByTypeApi('flow_category')
  } catch (error) {
    // 字典取不到不应阻断发起：退化为「未分类」一组
    logAsyncError('加载流程分类字典失败', error)
  }
}

/** 点击左侧分类：平滑滚到对应分区 */
function scrollToCategory(code: string) {
  const el = document.getElementById(`cat-${code}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    activeCategory.value = code
  }
}

/** 进入填单页：用 flowCode 作为路由参数，刷新后仍可直达 */
function openFlow(flow: FlowOptionVO) {
  void router.push(`/workflow/apply/${flow.flowCode}`)
}

// ---------------- 滚动高亮 ----------------
/**
 * 用 IntersectionObserver 做「当前分区」高亮，比监听 scroll + 计算 offsetTop 更省事，
 * 也不会因为卡片高度变化而错位；rootMargin 顶部留 80px，避免标题刚露头就切换。
 */
let observer: IntersectionObserver | null = null

function observeSections() {
  observer?.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible.length) {
        activeCategory.value = visible[0].target.id.replace('cat-', '')
      }
    },
    { rootMargin: '-80px 0px -60% 0px', threshold: 0 },
  )
  for (const g of groupedFlows.value) {
    const el = document.getElementById(`cat-${g.code}`)
    if (el) observer.observe(el)
  }
}

onMounted(async () => {
  await Promise.all([loadFlows(), loadCategories()])
  await nextTick()
  activeCategory.value = groupedFlows.value[0]?.code ?? ''
  observeSections()
})

onUnmounted(() => observer?.disconnect())
</script>

<template>
  <div class="apply">
    <aside class="apply__side">
      <ApplyCategoryNav :categories="navItems" :active="activeCategory" @select="scrollToCategory" />
    </aside>

    <main class="apply__main">
      <el-alert
        v-if="!flowOptions.length"
        type="info"
        show-icon
        :closable="false"
        title="暂无已启用的流程模板，请先在「流程模板」中新增并部署流程"
        class="apply__empty"
      />

      <section
        v-for="grp in groupedFlows"
        :id="`cat-${grp.code}`"
        :key="grp.code"
        class="apply__section"
      >
        <header class="apply__section-head">
          <h3 class="apply__section-title">{{ grp.label }}</h3>
          <span class="apply__section-count">{{ grp.flows.length }} 个流程</span>
        </header>
        <div class="apply__cards">
          <ApplyFlowCard v-for="f in grp.flows" :key="f.id" :flow="f" @open="openFlow" />
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.apply {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.apply__main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.apply__section {
  /* 锚点滚动时给标题留呼吸空间，避免贴顶 */
  scroll-margin-top: 12px;
  padding: 16px 18px;
  background: #fff;
  border-radius: 6px;
}

.apply__section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}

.apply__section-title {
  margin: 0;
  padding-left: 9px;
  border-left: 3px solid var(--el-color-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

.apply__section-count {
  font-size: 12px;
  color: #a8abb2;
}

.apply__cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.apply__empty {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .apply {
    grid-template-columns: minmax(0, 1fr);
  }

  .apply__side :deep(.apply-nav) {
    position: static;
  }

  .apply__side :deep(.apply-nav__list) {
    display: flex;
    gap: 8px;
    overflow-x: auto;
  }

  .apply__side :deep(.apply-nav__item) {
    flex: 0 0 auto;
    margin-bottom: 0;
  }
}
</style>
