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
      },
      // 上传文件的静态访问（头像等）：后端把存储目录挂在 /uploads/** 上，
      // 不代理的话开发态下 <img src="/uploads/..."> 会去 3000 找文件 → 404 → 头像显示不出来。
      // 生产环境由 nginx 把同一个前缀转到后端，口径保持一致。
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
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
