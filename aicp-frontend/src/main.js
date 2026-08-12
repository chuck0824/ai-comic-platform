import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
// 程序化调用不会被按需 CSS 自动引入
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 全局异常处理：防止未捕获异常导致白屏
app.config.errorHandler = (err, _vm, info) => {
  console.error('[Vue Error]', info, err)
  ElNotification.error({
    title: '页面异常',
    message: '页面出现异常，请刷新后重试',
    position: 'top-right'
  })
}

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
