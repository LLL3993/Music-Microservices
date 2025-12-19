import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'

const savedTheme = localStorage.getItem('theme')
const initialTheme =
  savedTheme === 'dark' || savedTheme === 'light'
    ? savedTheme
    : 'light'

document.documentElement.setAttribute('data-theme', initialTheme)

createApp(App).use(router).mount('#app')
