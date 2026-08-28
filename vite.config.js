import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const DEFAULT_API_PROXY_TARGET = 'http://111.170.148.177:58080'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = (env.VITE_API_PROXY_TARGET || DEFAULT_API_PROXY_TARGET).replace(/\/$/, '')

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
          configure(proxy) {
            // 浏览器请求 Vite 是同源的；转发到后端时移除 Origin，
            // 避免 Spring CORS 将开发代理误判为不受信任的跨域请求。
            proxy.on('proxyReq', proxyReq => proxyReq.removeHeader('origin'))
          }
        }
      }
    }
  }
})
