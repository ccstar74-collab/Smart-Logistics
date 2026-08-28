import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './AppShell.vue'
import router from './router'
import { useAuth } from './stores/auth-session'
import './style.css'
import './role-ui.css'

const { initializeSession } = useAuth()
await initializeSession()

window.addEventListener('auth:unauthorized', () => {
  if (router.currentRoute.value.path !== '/login') router.replace('/login')
})

createApp(App)
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
