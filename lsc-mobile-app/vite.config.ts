import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'node:path'

/**
 * 将根目录下的源码目录请求(api/stores/components/utils/config/styles)
 * 重写到 /src/ 下。原因：UniApp 的 @ 别名指向项目根(页面所在目录)，
 * 而本工程业务源码位于 src/，浏览器会请求 /api/xxx.ts 等根路径，
 * 需重写到 /src/api/xxx.ts 才能被 vite 正确编译。
 */
const rewriteSrcDirs = () => ({
  name: 'rewrite-src-dirs',
  configureServer(server: any) {
    server.middlewares.use((req: any, _res: any, next: any) => {
      // 仅重写源码模块请求(带扩展名), API 调用(无扩展名)走 vite proxy
      const extRe = /\.(ts|js|vue|scss|css|json|mjs|cjs)(\?|$)/
      const dirs = ['/api/', '/stores/', '/components/', '/utils/', '/config/', '/styles/']
      if (req.url && extRe.test(req.url)) {
        for (const d of dirs) {
          if (req.url.startsWith(d)) {
            req.url = '/src' + req.url
            break
          }
        }
      }
      next()
    })
  },
})

export default defineConfig({
  plugins: [uni(), rewriteSrcDirs()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`,
      },
    },
  },
})
