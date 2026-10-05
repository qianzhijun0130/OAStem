import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  // 本地开发端口
  server: {
    port: 3000,
    open: true, // 启动自动打开浏览器
    // 接口代理，解决跨域
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080', //后端地址
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  // 路径别名 @ 指向src
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: 'dist' //打包输出文件夹
  }
})
