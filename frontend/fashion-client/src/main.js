import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/global.css'

import AgentChat from '@/components/AgentChat.vue'

const app = createApp(App)
app.use(router)
app.use(ElementPlus)
app.component('AgentChat', AgentChat)
app.mount('#app')
