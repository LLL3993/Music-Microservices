<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const headerKeyword = ref(typeof route.query.q === 'string' ? route.query.q : '')

const isDiscoverActive = computed(() => route.name === 'Discover')
const isSearchActive = computed(() => route.name === 'Search')

const theme = ref(document.documentElement.getAttribute('data-theme') || 'dark')

const isDark = computed(() => theme.value === 'dark')

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
        <button class="btn ghost" type="button">登录</button>
        <button class="btn primary" type="button">注册</button>

        <div class="user">
          <img
            class="avatar"
            src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
            alt="User"
          />
          <div class="user-menu">
            <div class="menu-card"></div>
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
          <a class="nav-item" href="#" @click.prevent>我的收藏</a>
          <a class="nav-item" href="#" @click.prevent>歌单</a>
        </nav>
      </aside>

      <section class="content">
        <router-view />
      </section>
    </div>

    <footer class="playerbar"></footer>
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
  top: 42px;
  display: none;
}

.user:hover .user-menu {
  display: block;
}

.menu-card {
  width: 220px;
  height: 160px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
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
</style>
