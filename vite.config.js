import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const DEFAULT_API_PROXY_TARGET = 'http://111.170.148.177:58080'
const DEFAULT_ROUTE_AGENT_PROXY_TARGET = 'http://111.170.148.177:58081'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = (env.VITE_API_PROXY_TARGET || DEFAULT_API_PROXY_TARGET).replace(/\/$/, '')
  const routeAgentTarget = (env.VITE_ROUTE_AGENT_PROXY_TARGET || DEFAULT_ROUTE_AGENT_PROXY_TARGET).replace(/\/$/, '')

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      host: '0.0.0.0',
      watch: {
        ignored: ['**/.build-check-*/**', '**/dist/**']
      },
      proxy: {
        '/route-agent': {
          target: routeAgentTarget,
          changeOrigin: true,
          rewrite: path => path.replace(/^\/route-agent/, ''),
          configure(proxy) {
            proxy.on('proxyReq', proxyReq => proxyReq.removeHeader('origin'))
          }
        },
        '/api': {
          target,
          changeOrigin: true,
          configure(proxy) {
            proxy.on('proxyReq', proxyReq => proxyReq.removeHeader('origin'))
          }
        }
      }
    }
  }
})
