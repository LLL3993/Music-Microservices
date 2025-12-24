<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

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
const isAdminActive = computed(() => String(route.path || '').startsWith('/admin'))
const isAdminUsersActive = computed(() => route.name === 'AdminUsers')
const isAdminMusicActive = computed(() => route.name === 'AdminMusic')

const isAdmin = computed(() => Boolean(user.value?.isAdmin))

const adminMenuOpen = ref(false)

watch(
  () => route.path,
  (p) => {
    if (String(p || '').startsWith('/admin')) adminMenuOpen.value = true
  },
  { immediate: true },
)

function toggleAdminMenu() {
  adminMenuOpen.value = !adminMenuOpen.value
}

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

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

const brandLogoUrl = computed(() => `${baseUrl()}data/pic/icon.jpg`)
const userAvatarUrl = computed(() => `${baseUrl()}data/pic/user.jpg`)

function musicUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/music/${encodeURIComponent(song)}.mp3`
}

function lrcUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/lrc/${encodeURIComponent(song)}.lrc`
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

function safeParsePlayerState(raw) {
  const parsed = safeParseJson(raw || '')
  if (!parsed || typeof parsed !== 'object') return null
  return parsed
}

const playerState = ref({
  songName: '',
  artist: '',
  isPlaying: false,
  currentTime: 0,
  duration: 0,
  audioUrl: '',
  lrcUrl: '',
  coverUrl: '',
  queue: [],
  index: 0,
})

const audioEl = ref(null)

const lyricLoading = ref(false)
const lyricError = ref('')
const lyricItems = ref([])
const lyricActiveLine = ref(0)

const favoriteIdBySongName = ref({})

const activeLyricItem = computed(() => {
  const arr = lyricItems.value
  if (!Array.isArray(arr) || !arr.length) return null
  const idx = lyricActiveLine.value
  if (!Number.isFinite(idx) || idx < 0 || idx >= arr.length) return arr[0]
  return arr[idx]
})

const isSeeking = ref(false)
const seekingTime = ref(0)
const pendingSeek = ref(null)

function clamp(num, min, max) {
  if (!Number.isFinite(num)) return min
  return Math.min(max, Math.max(min, num))
}

function formatTime(sec) {
  const s = Number.isFinite(sec) ? sec : 0
  const total = Math.max(0, Math.floor(s))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function applyAudioSource(song, resetTime) {
  if (!audioEl.value) return
  const src = musicUrlBySong(song)
  if (!src) return

  const currentSrc = audioEl.value.getAttribute('src') || ''
  if (currentSrc !== src) {
    audioEl.value.setAttribute('src', src)
    audioEl.value.load()
  }

  if (resetTime) {
    audioEl.value.currentTime = 0
    playerState.value.currentTime = 0
  }
}

function parseLrc(text) {
  const out = []
  const lines = String(text || '').split(/\r?\n/)
  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) continue

    const tagMatches = Array.from(line.matchAll(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g))
    if (!tagMatches.length) continue

    const textPart = line.replace(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g, '').trim()
    for (const m of tagMatches) {
      const mm = Number(m[1])
      const ss = Number(m[2])
      const frac = m[3] ? Number(String(m[3]).padEnd(3, '0')) : 0
      if (!Number.isFinite(mm) || !Number.isFinite(ss) || !Number.isFinite(frac)) continue
      const t = mm * 60 + ss + frac / 1000
      out.push({ time: t, text: textPart })
    }
  }
  out.sort((a, b) => a.time - b.time)
  return out
}

async function loadLyrics(song) {
  lyricLoading.value = true
  lyricError.value = ''
  lyricItems.value = []
  lyricActiveLine.value = 0
  try {
    const resp = await fetch(lrcUrlBySong(song))
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const text = await resp.text()
    const parsed = parseLrc(text)
    lyricItems.value = parsed.length ? parsed : [{ time: 0, text: '暂无歌词' }]
  } catch {
    lyricError.value = '歌词加载失败'
    lyricItems.value = [{ time: 0, text: '暂无歌词' }]
  } finally {
    lyricLoading.value = false
  }
}

function persistPlayerState() {
  localStorage.setItem('player_state', JSON.stringify(playerState.value))
}

function emitPlayerState() {
  window.dispatchEvent(new CustomEvent('player:state', { detail: playerState.value }))
}

async function getArtistBySongName(songName) {
  if (!songName) return ''
  if (!token.value) return ''
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const { data } = await axios.get(`${apiBase}/api/meta/song-name`, {
      params: { songName },
      headers,
    })
    return typeof data?.artist === 'string' ? data.artist : ''
  } catch {
    return ''
  }
}

async function resolveArtistIfMissing(songName) {
  if (!songName) return
  if (playerState.value.songName !== songName) return
  if (typeof playerState.value.artist === 'string' && playerState.value.artist.trim()) return
  const artist = await getArtistBySongName(songName)
  if (!artist) return
  if (playerState.value.songName !== songName) return
  if (typeof playerState.value.artist === 'string' && playerState.value.artist.trim()) return
  playerState.value.artist = artist
  persistPlayerState()
  emitPlayerState()
}

function setPlayerState(next) {
  const nextSongName = typeof next?.songName === 'string' ? next.songName : ''
  const nextArtist = typeof next?.artist === 'string' ? next.artist : ''
  const audioUrl = musicUrlBySong(nextSongName)
  const lrcUrl = lrcUrlBySong(nextSongName)
  const coverUrl = coverUrlBySong(nextSongName)

  playerState.value = {
    songName: nextSongName,
    artist: nextArtist,
    isPlaying: Boolean(next?.isPlaying),
    currentTime: Number.isFinite(next?.currentTime) ? next.currentTime : 0,
    duration: Number.isFinite(next?.duration) ? next.duration : 0,
    audioUrl,
    lrcUrl,
    coverUrl,
    queue: Array.isArray(next?.queue) ? next.queue.filter((x) => typeof x === 'string') : [],
    index: Number.isFinite(next?.index) ? next.index : 0,
  }
  persistPlayerState()
  emitPlayerState()
}

function ensureQueueIndex() {
  if (!playerState.value.queue.length) return
  const idx = playerState.value.queue.findIndex((s) => s === playerState.value.songName)
  if (idx >= 0) playerState.value.index = idx
}

function openPlayer() {
  if (!playerState.value.songName) return
  router.push(`/player?name=${encodeURIComponent(playerState.value.songName)}`)
}

function startPlayback() {
  if (!audioEl.value || !playerState.value.songName) return
  applyAudioSource(playerState.value.songName, false)

  const p = audioEl.value.play()
  if (p && typeof p.then === 'function') {
    p.then(() => {
      playerState.value.isPlaying = true
      persistPlayerState()
      emitPlayerState()
    }).catch(() => {
      playerState.value.isPlaying = false
      persistPlayerState()
      emitPlayerState()
    })
  } else {
    playerState.value.isPlaying = true
    persistPlayerState()
    emitPlayerState()
  }
}

function pausePlayback() {
  if (!audioEl.value) return
  audioEl.value.pause()
  playerState.value.isPlaying = false
  persistPlayerState()
  emitPlayerState()
}

function togglePlay() {
  if (!playerState.value.songName) return
  if (playerState.value.isPlaying) pausePlayback()
  else startPlayback()
}

async function playPrev() {
  const q = playerState.value.queue
  if (!q.length) return
  ensureQueueIndex()
  const nextIndex = (playerState.value.index - 1 + q.length) % q.length
  playerState.value.index = nextIndex
  playerState.value.songName = q[nextIndex]
  playerState.value.isPlaying = true
  playerState.value.currentTime = 0
  playerState.value.audioUrl = musicUrlBySong(playerState.value.songName)
  playerState.value.lrcUrl = lrcUrlBySong(playerState.value.songName)
  playerState.value.coverUrl = coverUrlBySong(playerState.value.songName)
  persistPlayerState()
  emitPlayerState()
  loadLyrics(playerState.value.songName)
  openPlayer()
  applyAudioSource(playerState.value.songName, true)
  startPlayback()
}

async function playNext() {
  const q = playerState.value.queue
  if (!q.length) return
  ensureQueueIndex()
  const nextIndex = (playerState.value.index + 1) % q.length
  playerState.value.index = nextIndex
  playerState.value.songName = q[nextIndex]
  playerState.value.isPlaying = true
  playerState.value.currentTime = 0
  playerState.value.audioUrl = musicUrlBySong(playerState.value.songName)
  playerState.value.lrcUrl = lrcUrlBySong(playerState.value.songName)
  playerState.value.coverUrl = coverUrlBySong(playerState.value.songName)
  persistPlayerState()
  emitPlayerState()
  loadLyrics(playerState.value.songName)
  openPlayer()
  applyAudioSource(playerState.value.songName, true)
  startPlayback()
}

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
  loadFavorites()
}

function logout() {
  token.value = ''
  user.value = null
  localStorage.removeItem('auth_token')
  localStorage.removeItem('auth_user')
  loadFavorites()
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

function onPlayerSet(e) {
  const detail = e?.detail || {}
  const songName = typeof detail.songName === 'string' ? detail.songName : ''
  const artist = typeof detail.artist === 'string' ? detail.artist : ''
  const queue = Array.isArray(detail.queue) ? detail.queue : []
  const index = Number.isFinite(detail.index) ? detail.index : 0
  const shouldPlay = typeof detail.isPlaying === 'boolean' ? detail.isPlaying : true

  setPlayerState({
    songName,
    artist,
    queue,
    index,
    currentTime: 0,
    isPlaying: shouldPlay,
  })

  applyAudioSource(songName, true)
  if (songName) loadLyrics(songName)
  if (songName && !artist) resolveArtistIfMissing(songName)
  if (shouldPlay) startPlayback()
  else pausePlayback()
}

function onPlayerToggle() {
  togglePlay()
}

function onPlayerPrev() {
  playPrev()
}

function onPlayerNext() {
  playNext()
}

function seekTo(time, options) {
  if (!audioEl.value) return
  if (!Number.isFinite(time)) return
  const audioDuration = Number.isFinite(audioEl.value.duration) ? audioEl.value.duration : 0
  const stateDuration = Number.isFinite(playerState.value.duration) ? playerState.value.duration : 0
  const max = audioDuration > 0 ? audioDuration : stateDuration > 0 ? stateDuration : Number.POSITIVE_INFINITY

  const nextTime = clamp(time, 0, max)
  const shouldPersist = options?.persist !== false

  if (max === Number.POSITIVE_INFINITY) {
    pendingSeek.value = { time: nextTime, persist: shouldPersist }
  } else {
    pendingSeek.value = null
  }

  try {
    audioEl.value.currentTime = nextTime
  } catch {}
  playerState.value.currentTime = nextTime
  if (shouldPersist) persistPlayerState()
  emitPlayerState()
}

function onPlayerSeek(e) {
  seekTo(e?.detail?.time, { persist: e?.detail?.persist })
}

let lastPersistSecond = -1

function onAudioLoadedMetadata() {
  if (!audioEl.value) return
  const d = Number.isFinite(audioEl.value.duration) ? audioEl.value.duration : 0
  playerState.value.duration = d
  const pending = pendingSeek.value
  if (pending && Number.isFinite(pending.time)) {
    pendingSeek.value = null
    seekTo(pending.time, { persist: pending.persist })
  }
  persistPlayerState()
  emitPlayerState()
}

function onAudioTimeUpdate() {
  if (!audioEl.value) return
  const t = Number.isFinite(audioEl.value.currentTime) ? audioEl.value.currentTime : 0
  const d = Number.isFinite(audioEl.value.duration) ? audioEl.value.duration : 0
  if (!isSeeking.value) playerState.value.currentTime = t
  playerState.value.duration = d
  emitPlayerState()

  const arr = lyricItems.value
  if (arr.length) {
    let idx = 0
    for (let i = 0; i < arr.length; i += 1) {
      if (arr[i].time <= t + 0.05) idx = i
      else break
    }
    if (idx !== lyricActiveLine.value) {
      lyricActiveLine.value = idx
    }
  }

  const sec = Math.floor(t)
  if (sec !== lastPersistSecond) {
    lastPersistSecond = sec
    persistPlayerState()
  }
}

function onAudioEnded() {
  if (playerState.value.queue.length) {
    playNext()
    return
  }
  pausePlayback()
}

function onAudioError() {
  playerState.value.isPlaying = false
  playerState.value.duration = 0
  persistPlayerState()
  emitPlayerState()
}

function isFavorited(songName) {
  return Boolean(favoriteIdBySongName.value?.[songName])
}

async function loadFavorites() {
  if (!token.value) {
    favoriteIdBySongName.value = {}
    return
  }
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const { data } = await axios.get(`${apiBase}/api/favorites/username`, { headers })
    const arr = Array.isArray(data) ? data : []
    const map = {}
    for (const f of arr) {
      if (!f || !f.songName) continue
      map[f.songName] = f.id
    }
    favoriteIdBySongName.value = map
  } catch {
    favoriteIdBySongName.value = {}
  }
}

async function toggleFavorite(songName) {
  if (!songName) return
  if (!token.value) return
  try {
    const headers = { Authorization: `Bearer ${token.value}` }
    const existingId = favoriteIdBySongName.value?.[songName]
    if (existingId) {
      await axios.delete(`${apiBase}/api/favorites/${existingId}`, { headers })
      const next = { ...(favoriteIdBySongName.value || {}) }
      delete next[songName]
      favoriteIdBySongName.value = next
    } else {
      const { data } = await axios.post(`${apiBase}/api/favorites`, { songName }, { headers })
      const id = data?.id
      if (id != null) {
        favoriteIdBySongName.value = { ...(favoriteIdBySongName.value || {}), [songName]: id }
      }
    }
  } catch {}
}

function onProgressDown() {
  isSeeking.value = true
  seekingTime.value = Number.isFinite(playerState.value.currentTime) ? playerState.value.currentTime : 0
}

function onProgressInput(e) {
  const raw = Number(e?.target?.value)
  if (!Number.isFinite(raw)) return
  const nextTime = clamp(
    raw,
    0,
    Number.isFinite(playerState.value.duration) ? playerState.value.duration : 0,
  )
  seekingTime.value = nextTime
  seekTo(nextTime, { persist: false })
}

function onProgressUp() {
  if (!isSeeking.value) return
  isSeeking.value = false
  seekTo(seekingTime.value)
}

function onCurrentLyricClick() {
  const item = activeLyricItem.value
  if (!item) return
  if (!Number.isFinite(item.time)) return
  if (!playerState.value.songName) return
  if (lyricLoading.value) return
  if (lyricError.value) return
  seekTo(item.time)
}

onMounted(() => {
  const saved = safeParsePlayerState(localStorage.getItem('player_state') || '')
  if (saved) {
    setPlayerState({
      ...playerState.value,
      ...saved,
      isPlaying: Boolean(saved.isPlaying),
    })
  } else {
    emitPlayerState()
  }

  window.addEventListener('player:set', onPlayerSet)
  window.addEventListener('player:toggle', onPlayerToggle)
  window.addEventListener('player:prev', onPlayerPrev)
  window.addEventListener('player:next', onPlayerNext)
  window.addEventListener('player:seek', onPlayerSeek)

  if (playerState.value.songName) {
    loadLyrics(playerState.value.songName)
    if (!playerState.value.artist) resolveArtistIfMissing(playerState.value.songName)
  }
  loadFavorites()
})

onBeforeUnmount(() => {
  window.removeEventListener('player:set', onPlayerSet)
  window.removeEventListener('player:toggle', onPlayerToggle)
  window.removeEventListener('player:prev', onPlayerPrev)
  window.removeEventListener('player:next', onPlayerNext)
  window.removeEventListener('player:seek', onPlayerSeek)
})
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="topbar-left">
        <div class="brand" @click="router.push({ name: 'Discover' })">
          <img
            class="brand-logo"
            :src="brandLogoUrl"
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
          <img class="avatar" :src="userAvatarUrl" alt="User" />
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
          <a
            v-if="isAuthed && isAdmin"
            class="nav-item nav-parent"
            :class="{ active: isAdminActive, open: adminMenuOpen }"
            href="/admin"
            @click.prevent="toggleAdminMenu"
          >
            <span>管理界面</span>
            <span class="caret">▾</span>
          </a>
          <div v-if="isAuthed && isAdmin && adminMenuOpen" class="nav-sub">
            <a
              class="nav-item sub"
              :class="{ active: isAdminUsersActive }"
              href="/admin/users"
              @click.prevent="router.push({ name: 'AdminUsers' })"
              >用户管理</a
            >
            <a
              class="nav-item sub"
              :class="{ active: isAdminMusicActive }"
              href="/admin/music"
              @click.prevent="router.push({ name: 'AdminMusic' })"
              >音乐管理</a
            >
          </div>
        </nav>
      </aside>

      <section class="content">
        <router-view />
      </section>
    </div>

    <footer class="playerbar">
      <div class="playerbar-inner">
        <div class="playerbar-left" @click="openPlayer">
          <img
            class="playerbar-cover"
            :src="
              playerState.coverUrl ||
              'https://dummyimage.com/200x200/999999/ff4400.png&text=MUSIC'
            "
            alt="cover"
          />
          <div class="playerbar-meta">
            <div class="playerbar-title">
              {{ playerState.songName || '未选择歌曲' }}
            </div>
            <div class="playerbar-sub">
              {{ playerState.artist || (playerState.songName ? '歌手加载中...' : '点击任意播放按钮开始') }}
            </div>
          </div>
        </div>

        <div class="playerbar-controls">
          <div
            class="playerbar-current-lyric"
            :class="{ disabled: !playerState.songName || lyricLoading || lyricError }"
            @click="onCurrentLyricClick"
          >
            <span v-if="!playerState.songName">未选择歌曲</span>
            <span v-else-if="lyricLoading">歌词加载中...</span>
            <span v-else-if="lyricError">{{ lyricError }}</span>
            <span v-else>{{ activeLyricItem?.text || '暂无歌词' }}</span>
          </div>

          <div class="playerbar-buttons">
            <button
              class="playerbar-btn"
              type="button"
              :disabled="!playerState.queue.length"
              @click="playPrev"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M6 6v12M18 6l-8.5 6L18 18V6Z"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>

            <button
              class="playerbar-btn primary"
              type="button"
              :disabled="!playerState.songName"
              @click="togglePlay"
            >
              <svg v-if="playerState.isPlaying" width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M8 5v14M16 5v14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M8 5v14l12-7L8 5Z" fill="currentColor" />
              </svg>
            </button>

            <button
              class="playerbar-btn"
              type="button"
              :disabled="!playerState.queue.length"
              @click="playNext"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M18 6v12M6 6l8.5 6L6 18V6Z"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>

            <button
              class="playerbar-btn"
              type="button"
              :disabled="!playerState.songName || !isAuthed"
              :class="{ active: isFavorited(playerState.songName) }"
              @click="toggleFavorite(playerState.songName)"
            >
              <svg v-if="isFavorited(playerState.songName)" width="18" height="18" viewBox="0 0 24 24">
                <path
                  d="M12 21s-7-4.4-9.5-8.3C.5 9.4 2.2 6 6 6c2.1 0 3.4 1.2 4 2 0.6-0.8 1.9-2 4-2 3.8 0 5.5 3.4 3.5 6.7C19 16.6 12 21 12 21Z"
                  fill="currentColor"
                />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M12 20s-7-4.2-9.1-7.8C1 9.2 2.7 6 6.2 6c2 0 3.2 1.1 3.8 1.9C10.6 7.1 11.8 6 13.8 6c3.5 0 5.2 3.2 3.3 6.2C19 15.8 12 20 12 20Z"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </div>
        </div>

        <div class="playerbar-right">
          <div class="playerbar-progress">
            <input
              class="playerbar-range"
              type="range"
              min="0"
              :max="playerState.duration || 0"
              step="0.01"
              :value="isSeeking ? seekingTime : playerState.currentTime || 0"
              :disabled="!playerState.songName || !playerState.duration"
              @pointerdown="onProgressDown"
              @pointerup="onProgressUp"
              @pointercancel="onProgressUp"
              @change="onProgressUp"
              @input="onProgressInput"
            />
            <div class="playerbar-time">
              <span>{{ formatTime(playerState.currentTime) }}</span>
              <span>{{ formatTime(playerState.duration) }}</span>
            </div>
          </div>
        </div>
      </div>

      <audio
        ref="audioEl"
        class="playerbar-audio"
        :src="playerState.audioUrl"
        @loadedmetadata="onAudioLoadedMetadata"
        @timeupdate="onAudioTimeUpdate"
        @ended="onAudioEnded"
        @error="onAudioError"
      ></audio>
    </footer>
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
  top: 32px;
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

.nav-item.nav-parent {
  justify-content: space-between;
}

.nav-item.nav-parent .caret {
  font-size: 12px;
  color: var(--muted);
}

.nav-sub {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 10px;
  margin-top: -2px;
}

.nav-item.sub {
  height: 36px;
  font-size: 13px;
  opacity: 0.95;
}

.content {
  flex: 1;
  padding: 18px;
  overflow: auto;
}

.playerbar {
  height: 140px;
  background: var(--panel);
  border-top: 1px solid var(--border);
}

.playerbar-inner {
  height: 100%;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto minmax(280px, 1fr);
  align-items: stretch;
  gap: 14px;
  padding: 0 18px;
  max-width: 1120px;
  margin: 0 auto;
}

.playerbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  cursor: pointer;
}

.playerbar-cover {
  height: 44px;
  width: 44px;
  border-radius: 12px;
  border: 1px solid var(--border);
  object-fit: cover;
  flex: none;
}

.playerbar-meta {
  min-width: 0;
}

.playerbar-title {
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playerbar-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playerbar-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.playerbar-current-lyric {
  height: 40px;
  width: 300px;
  max-width: 34vw;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  padding: 0 12px;
  display: flex;
  align-items: center;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  user-select: none;
}

.playerbar-current-lyric.disabled {
  opacity: 0.75;
  cursor: default;
}

.playerbar-current-lyric:not(.disabled):hover {
  border-color: var(--accent);
  color: var(--accent);
}

.playerbar-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.playerbar-btn {
  height: 40px;
  width: 40px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.playerbar-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.playerbar-btn.primary {
  background: var(--accent);
  border-color: transparent;
  color: #fff;
}

.playerbar-btn.active {
  color: var(--accent);
}

.playerbar-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.playerbar-right {
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: center;
  padding: 12px 0;
}

.playerbar-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 520px;
}

.playerbar-range {
  width: 100%;
  accent-color: var(--accent);
}

.playerbar-time {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted);
}

.playerbar-audio {
  display: none;
}

@media (max-width: 980px) {
  .playerbar-inner {
    grid-template-columns: 1fr auto;
  }
  .playerbar-progress {
    display: none;
  }
  .playerbar-current-lyric {
    display: none;
  }
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
