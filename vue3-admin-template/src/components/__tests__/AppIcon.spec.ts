import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AppIcon from '@/components/icons/AppIcon.vue'

/**
 * AppIcon 是全站图标的唯一出口，三个行为必须稳定：
 * 1. 能解析的名字渲染出 svg（而不是空节点）；
 * 2. 历史 EP 名同样能渲染（库里存量数据仍是旧名）；
 * 3. **解析不了的名字不渲染任何东西**——不能留空方框占位。
 */
describe('components/AppIcon', () => {
  it('已知 Lucide 名渲染 svg，并带统一 class 与 aria-hidden', () => {
    const wrapper = mount(AppIcon, { props: { name: 'FileText' } })
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.classes()).toContain('app-icon')
    expect(svg.attributes('aria-hidden')).toBe('true')
  })

  it('历史 EP 名（Document）同样可以渲染', () => {
    const wrapper = mount(AppIcon, { props: { name: 'Document' } })
    expect(wrapper.find('svg').exists()).toBe(true)
  })

  it('未知图标名不渲染任何内容', () => {
    const wrapper = mount(AppIcon, { props: { name: 'NoSuchIcon' } })
    expect(wrapper.html()).toBe('<!--v-if-->')
  })

  it('size 与 strokeWidth 透传给 svg', () => {
    const wrapper = mount(AppIcon, { props: { name: 'User', size: 22, strokeWidth: 1.5 } })
    const svg = wrapper.find('svg')
    expect(svg.attributes('width')).toBe('22')
    expect(svg.attributes('height')).toBe('22')
    expect(svg.attributes('stroke-width')).toBe('1.5')
  })

  it('默认尺寸为 16', () => {
    const wrapper = mount(AppIcon, { props: { name: 'User' } })
    expect(wrapper.find('svg').attributes('width')).toBe('16')
  })
})

/**
 * 兜底日志约定：catch 里只记录、不再弹窗，且必须带固定前缀，
 * 自动化测试靠这个前缀区分「已兜底」与「真未捕获」。
 */
describe('utils/asyncGuard', () => {
  it('logAsyncError 以固定前缀写入 console.error', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { ASYNC_ERROR_PREFIX, logAsyncError } = await import('@/utils/asyncGuard')
    const error = new Error('boom')

    logAsyncError('加载公告失败', error)

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0]).toBe(`${ASYNC_ERROR_PREFIX} 加载公告失败`)
    expect(spy.mock.calls[0][1]).toBe(error)
  })
})
