import { createApp } from 'vue'
import './style.css'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'

function setRootFontSize() {
  const maxWidth = 500
  const fontSize = Math.min(window.innerWidth, maxWidth) / 10 + 'px'
  document.documentElement.style.fontSize = fontSize
}

setRootFontSize()
window.addEventListener('resize', setRootFontSize)

createApp(App).use(router).mount('#app')
