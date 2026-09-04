import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import './style.css'
import '@fortawesome/fontawesome-free/css/all.css'
import { applyCachedBrandTheme } from './composables/useBrandTheme'

// Restore the admin-configured brand scheme before first paint (no teal flash
// when a non-default scheme was saved). Live refresh happens in App.vue.
applyCachedBrandTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
