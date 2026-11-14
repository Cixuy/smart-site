import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './assets/tailwind.css'
import './assets/base.css'
import './assets/main.css'
import permission from './directives/permission'
const app = createApp(App)

// ==================== 临时诊断代码（定位"编辑器切出后空白页"问题，定位后删除） ====================
function showDiagError(title, err) {
  try {
    const msg = (err && (err.stack || err.message || String(err))) || String(err)
    const div = document.createElement('div')
    div.style.cssText =
      'position:fixed;top:0;right:0;z-index:99999;max-width:80%;max-height:50vh;overflow:auto;' +
      'background:rgba(220,38,38,0.95);color:#fff;padding:10px 14px;font-size:12px;' +
      'font-family:monospace;white-space:pre-wrap;border-bottom-left-radius:8px;' +
      'box-shadow:0 4px 20px rgba(0,0,0,0.4);cursor:pointer'
    div.textContent = `[${title}] ${new Date().toLocaleTimeString()}\n${msg}`
    div.onclick = () => div.remove()
    document.body.appendChild(div)
    setTimeout(() => div.remove(), 20000)
  } catch (_) {}
}
app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue全局错误]', err, info)
  showDiagError('Vue错误', err)
}
window.addEventListener('error', e => {
  console.error('[window错误]', e.error || e.message)
  showDiagError('JS错误', e.error || e.message)
})
window.addEventListener('unhandledrejection', e => {
  // flv.js 已知无害 bug：销毁播放器（视频轮播切换/组件卸载）后，其内部 Web Worker
  // 异步回调访问已移除的 SourceBuffer 抛 InvalidStateError，不影响播放与切换。
  // 静默处理，避免误报为真实错误（其余 Promise 错误照常诊断）
  const msg = (e.reason && (e.reason.message || String(e.reason))) || String(e.reason)
  if (msg.includes('SourceBuffer') && msg.includes('removed from the parent media source')) {
    e.preventDefault()
    return
  }
  console.error('[Promise错误]', e.reason)
  showDiagError('Promise错误', e.reason)
})
// ==================== 临时诊断代码结束 ====================

// 注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.use(store)
app.use(ElementPlus, { size: 'default', locale: zhCn })
app.use(permission)

app.mount('#app')
