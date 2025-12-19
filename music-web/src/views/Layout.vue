<script setup>
import axios from 'axios'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090'

function safeParseJson(value) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

const token = ref(localStorage.getItem('auth_token') || '')
const user = ref(safeParseJson(localStorage.getItem('auth_user') || 'null'))

const isAuthed = computed(() => Boolean(token.value))

const headerKeyword = ref(typeof route.query.q === 'string' ? route.query.q : '')

const isDiscoverActive = computed(() => route.name === 'Discover')
const isSearchActive = computed(() => route.name === 'Search')
const isFavoritesActive = computed(() => route.name === 'Favorites')
const isPlaylistsActive = computed(() => route.name === 'Playlists')

const theme = ref(document.documentElement.getAttribute('data-theme') || 'dark')

const isDark = computed(() => theme.value === 'dark')

const authOpen = ref(false)
const authMode = ref('login')
const submitting = ref(false)
const formError = ref('')

const loginForm = ref({
  username: '',
  password: '',
})
const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.setAttribute('data-theme', theme.value)
  localStorage.setItem('theme', theme.value)
}

function onHeaderSearchEnter() {
  const key = headerKeyword.value.trim()
  if (!key) return
  router.push(`/search?q=${encodeURIComponent(key)}`)
}

function openLogin() {
  authMode.value = 'login'
  authOpen.value = true
  formError.value = ''
}

function openRegister() {
  authMode.value = 'register'
  authOpen.value = true
  formError.value = ''
}

function closeAuth() {
  authOpen.value = false
  submitting.value = false
  formError.value = ''
}

function switchToRegister() {
  authMode.value = 'register'
  formError.value = ''
}

function switchToLogin() {
  authMode.value = 'login'
  formError.value = ''
}

function saveAuth(authToken, authUser) {
  token.value = authToken || ''
  user.value = authUser || null
  localStorage.setItem('auth_token', token.value)
  localStorage.setItem('auth_user', JSON.stringify(user.value))
}

function logout() {
  token.value = ''
  user.value = null
  localStorage.removeItem('auth_token')
  localStorage.removeItem('auth_user')
}

function pickErrorMessage(err) {
  const message = err?.response?.data?.message
  if (typeof message === 'string' && message.trim()) return message
  return '请求失败'
}

async function submitLogin() {
  const username = loginForm.value.username.trim()
  const password = loginForm.value.password.trim()
  if (!username || !password) {
    formError.value = '用户名和密码为必填'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const { data } = await axios.post(`${apiBase}/api/auth/login`, {
      username,
      password,
    })
    saveAuth(data?.token, data?.user)
    closeAuth()
  } catch (err) {
    formError.value = pickErrorMessage(err)
  } finally {
    submitting.value = false
  }
}

async function submitRegister() {
  const username = registerForm.value.username.trim()
  const email = registerForm.value.email.trim()
  const password = registerForm.value.password.trim()
  const confirmPassword = registerForm.value.confirmPassword.trim()

  if (!username || !email || !password || !confirmPassword) {
    formError.value = '用户名、邮箱、密码、确认密码为必填'
    return
  }
  if (password !== confirmPassword) {
    formError.value = '两次输入的密码不一致'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const { data } = await axios.post(`${apiBase}/api/auth/register`, {
      username,
      email,
      password,
    })
    saveAuth(data?.token, data?.user)
    closeAuth()
  } catch (err) {
    formError.value = pickErrorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="topbar-left">
        <div class="brand" @click="router.push({ name: 'Discover' })">
          <img
            class="brand-logo"
            src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
            alt="MUSIC"
          />
        </div>
        <div class="topbar-search">
          <input
            v-model="headerKeyword"
            class="input"
            type="text"
            placeholder="搜索歌曲 / 歌手（模糊查询）"
            @keyup.enter="onHeaderSearchEnter"
          />
        </div>
      </div>

      <div class="topbar-right">
        <template v-if="!isAuthed">
          <button class="btn ghost" type="button" @click="openLogin">登录</button>
          <button class="btn primary" type="button" @click="openRegister">注册</button>
        </template>

        <div v-if="isAuthed" class="user">
          <div class="user-name">{{ user?.username || '已登录' }}</div>
          <img class="avatar" src="https://dummyimage.com/80x80/999999/ff4400.png&text=U" alt="User" />
          <div class="user-menu">
            <div class="menu-card">
              <button class="menu-btn" type="button" @click="logout">退出登录</button>
            </div>
          </div>
        </div>

        <button class="icon-btn" type="button" @click="toggleTheme">
          <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path
              d="M21 14.5A8.5 8.5 0 0 1 9.5 3a7 7 0 1 0 11.5 11.5Z"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path
              d="M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12Z"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
    </header>

    <div class="main">
      <aside class="sidebar">
        <nav class="nav">
          <a
            class="nav-item"
            :class="{ active: isDiscoverActive }"
            href="/discover"
            @click.prevent="router.push({ name: 'Discover' })"
            >发现音乐</a
          >
          <a
            class="nav-item"
            :class="{ active: isSearchActive }"
            href="/search"
            @click.prevent="router.push({ name: 'Search' })"
            >搜索</a
          >
          <a
            class="nav-item"
            :class="{ active: isFavoritesActive }"
            href="/favorites"
            @click.prevent="router.push({ name: 'Favorites' })"
            >我的收藏</a
          >
          <a
            class="nav-item"
            :class="{ active: isPlaylistsActive }"
            href="/playlists"
            @click.prevent="router.push({ name: 'Playlists' })"
            >歌单</a
          >
        </nav>
      </aside>

      <section class="content">
        <router-view />
      </section>
    </div>

    <footer class="playerbar"></footer>
  </div>

  <div v-if="authOpen" class="modal-mask">
    <div class="modal">
      <div class="modal-head">
        <div class="modal-title">{{ authMode === 'login' ? '登录' : '注册' }}</div>
        <button class="modal-close" type="button" @click="closeAuth">×</button>
      </div>

      <div v-if="authMode === 'login'" class="form">
        <div class="field">
          <div class="label">用户名</div>
          <input v-model="loginForm.username" class="field-input" placeholder="请输入用户名" />
        </div>
        <div class="field">
          <div class="label">密码</div>
          <input v-model="loginForm.password" class="field-input" type="password" placeholder="请输入密码" />
        </div>

        <div class="form-error" v-if="formError">{{ formError }}</div>

        <button class="submit-btn" type="button" :disabled="submitting" @click="submitLogin">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
        <button class="switch-btn" type="button" @click="switchToRegister">去注册</button>
      </div>

      <div v-else class="form">
        <div class="field">
          <div class="label">用户名</div>
          <input v-model="registerForm.username" class="field-input" placeholder="请输入用户名" />
        </div>
        <div class="field">
          <div class="label">邮箱</div>
          <input v-model="registerForm.email" class="field-input" placeholder="请输入邮箱" />
        </div>
        <div class="field">
          <div class="label">密码</div>
          <input v-model="registerForm.password" class="field-input" type="password" placeholder="请输入密码" />
        </div>
        <div class="field">
          <div class="label">确认密码</div>
          <input
            v-model="registerForm.confirmPassword"
            class="field-input"
            type="password"
            placeholder="请再次输入密码"
          />
        </div>

        <div class="form-error" v-if="formError">{{ formError }}</div>

        <button class="submit-btn" type="button" :disabled="submitting" @click="submitRegister">
          {{ submitting ? '注册中...' : '注册' }}
        </button>
        <button class="switch-btn" type="button" @click="switchToLogin">去登录</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--bg);
  color: var(--text);
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-bottom: 1px solid var(--border);
  background: var(--panel);
  position: sticky;
  top: 0;
  z-index: 10;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 520px;
}

.brand {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.brand-logo {
  height: 28px;
  width: auto;
  display: block;
}

.topbar-search {
  flex: 1;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.input {
  width: 100%;
  height: 36px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  padding: 0 12px;
  outline: none;
}

.input::placeholder {
  color: var(--muted);
}

.btn {
  height: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  padding: 0 12px;
  cursor: pointer;
}

.btn.primary {
  background: var(--accent);
  color: white;
  border-color: transparent;
}

.btn.ghost {
  background: transparent;
}

.icon-btn {
  height: 34px;
  width: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.user {
  position: relative;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--muted);
  font-size: 12px;
}

.avatar {
  height: 34px;
  width: 34px;
  border-radius: 999px;
  border: 1px solid var(--border);
  display: block;
  object-fit: cover;
}

.user-menu {
  position: absolute;
  right: 0;
  top: 36px;
  display: none;
}

.user:hover .user-menu {
  display: block;
}

.menu-card {
  width: 220px;
  padding: 10px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.menu-btn {
  width: 100%;
  height: 36px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
}

.menu-btn:hover {
  border-color: var(--accent);
}

.main {
  flex: 1;
  display: flex;
  min-height: 0;
}

.sidebar {
  width: 220px;
  border-right: 1px solid var(--border);
  padding: 16px 12px;
  background: var(--panel);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 12px;
  border-radius: 12px;
  color: var(--text);
  text-decoration: none;
  background: transparent;
  border: 1px solid transparent;
}

.nav-item:hover {
  background: var(--card);
  border-color: var(--border);
}

.nav-item.active {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  border-color: color-mix(in srgb, var(--accent) 30%, var(--border));
  color: var(--text);
}

.content {
  flex: 1;
  padding: 18px;
  overflow: auto;
}

.playerbar {
  height: 68px;
  background: var(--panel);
  border-top: 1px solid var(--border);
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, #000 50%, transparent);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
}

.modal {
  width: 420px;
  max-width: 100%;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 14px;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.modal-title {
  font-weight: 700;
}

.modal-close {
  height: 34px;
  width: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field .label {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 12px;
  outline: none;
}

.form-error {
  color: #d44;
  font-size: 12px;
}

.submit-btn {
  height: 40px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.switch-btn {
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text);
  cursor: pointer;
}

.switch-btn:hover {
  border-color: var(--accent);
}
</style>
