import { createApp } from 'vue'
import App from './App.vue'
import { initializeTheme } from './composables/useTheme'

import '@fontsource/inter/latin-300.css'
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'
import '@fontsource/playfair-display/latin-400-italic.css'
import '@fontsource/playfair-display/latin-500-italic.css'
import '@fontsource/playfair-display/latin-600-italic.css'
import 'element-plus/dist/index.css'
import './assets/main.css'

import router from './router'

import { createPinia } from 'pinia'
const pinia = createPinia()

initializeTheme()
const app = createApp(App)
app.use(pinia).use(router)
app.mount('#app')
