import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * 前端单元测试配置（Vitest）。
 *
 * <p><b>为什么独立于 vite.config.ts</b>：开发配置里有 `server.proxy` 与 `open: true`，
 * 测试完全用不上；独立文件也避免「为了跑测试去改构建配置」这种耦合。
 * 别名必须与 `vite.config.ts` 保持一致，否则测试里 `@/utils/icons` 会解析失败。</p>
 *
 * <p><b>环境用 jsdom</b>：组件测试需要真实 DOM（`mount` 会创建元素），
 * 纯函数测试在 jsdom 下同样可用，因此不必按文件切换 environment。</p>
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/__tests__/**/*.spec.ts'],
    // 每个用例前恢复 mock，避免 spy 串味（例如 console.error 的 spy 污染下一条断言）
    restoreMocks: true,
    css: false,
  },
})
