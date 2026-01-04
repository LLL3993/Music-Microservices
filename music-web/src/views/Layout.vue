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
  playMode: 'order',
  currentTime: 0,
  duration: 0,
  audioUrl: '',
  lrcUrl: '',
  coverUrl: '',
  queue: [],
  index: 0,
  queueSource: null,
})

const audioEl = ref(null)

const lyricLoading = ref(false)
const lyricError = ref('')
const lyricItems = ref([])
const lyricActiveLine = ref(0)

const favoriteIdBySongName = ref({})

const queueSourceOpen = ref(false)
const queueSourceTab = ref('mine')
const queueSourceDisplayTab = ref('mine')
const queueSourceLoading = ref(false)
const queueSourceError = ref('')
const queueSourceMinePlaylists = ref([])
const queueSourcePublicPlaylists = ref([])
const queueSourceFavoriteSongs = ref([])
const queueSourceApplying = ref(false)
const queueSourceApplyError = ref('')

const activeQueueSource = computed(() => {
  const src = playerState.value?.queueSource
  if (!src || typeof src !== 'object') return null
  const type = src.type === 'favorites' || src.type === 'playlist' ? src.type : ''
  if (!type) return null
  if (type === 'favorites') return { type }
  const playlistName = typeof src.playlistName === 'string' ? src.playlistName.trim() : ''
  const username = typeof src.username === 'string' ? src.username.trim() : ''
  if (!playlistName || !username) return null
  return { type, playlistName, username }
})

const isQueueSourceFavoritesActive = computed(() => activeQueueSource.value?.type === 'favorites')

function isQueueSourcePlaylistActive(p) {
  const cur = activeQueueSource.value
  if (!cur || cur.type !== 'playlist') return false
  const playlistName = typeof p?.playlistName === 'string' ? p.playlistName.trim() : ''
  const username = typeof p?.username === 'string' ? p.username.trim() : ''
  return Boolean(playlistName && username && playlistName === cur.playlistName && username === cur.username)
}

const queueSourceDisplayedPlaylists = computed(() => {
  return queueSourceDisplayTab.value === 'public' ? queueSourcePublicPlaylists.value : queueSourceMinePlaylists.value
})

const queueSourceHasDisplayedContent = computed(() => {
  if (queueSourceDisplayTab.value === 'mine') return true
  return queueSourceDisplayedPlaylists.value.length > 0
})

const isNavigating = ref(false)

const toastOpen = ref(false)
const toastMessage = ref('')
let toastTimer = null

function showToast(message) {
  const msg = typeof message === 'string' ? message.trim() : ''
  if (!msg) return

  toastMessage.value = msg
  toastOpen.value = true
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastOpen.value = false
    toastTimer = window.setTimeout(() => {
      toastMessage.value = ''
      toastTimer = null
    }, 200)
  }, 1600)
}

function onToastShow(e) {
  showToast(e?.detail?.message)
}

function emitFavoritesChanged(detail) {
  window.dispatchEvent(new CustomEvent('favorites:changed', { detail: { ...(detail || {}), source: 'layout' } }))
}

function onFavoritesChanged(e) {
  const detail = e?.detail || {}
  if (detail?.source === 'layout') return
  if (detail && typeof detail === 'object' && detail.map && typeof detail.map === 'object') {
    favoriteIdBySongName.value = detail.map
    return
  }
  const songName = typeof detail?.songName === 'string' ? detail.songName : ''
  if (!songName) return
  const id = detail?.id
  const next = { ...(favoriteIdBySongName.value || {}) }
  if (id == null) delete next[songName]
  else next[songName] = id
  favoriteIdBySongName.value = next
}

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
    lyricItems.value = parsed.length ? parsed : [{ time: 0, text: '该歌曲暂无歌词' }]
  } catch {
    lyricError.value = '该歌曲暂无歌词'
    lyricItems.value = [{ time: 0, text: '该歌曲暂无歌词' }]
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
  const queueSource = next?.queueSource && typeof next.queueSource === 'object' ? next.queueSource : null
  const playMode = next?.playMode === 'shuffle' || next?.playMode === 'loop' || next?.playMode === 'order'
    ? next.playMode
    : playerState.value.playMode === 'shuffle' || playerState.value.playMode === 'loop' || playerState.value.playMode === 'order'
      ? playerState.value.playMode
      : 'order'

  playerState.value = {
    songName: nextSongName,
    artist: nextArtist,
    isPlaying: Boolean(next?.isPlaying),
    playMode,
    currentTime: Number.isFinite(next?.currentTime) ? next.currentTime : 0,
    duration: Number.isFinite(next?.duration) ? next.duration : 0,
    audioUrl,
    lrcUrl,
    coverUrl,
    queue: Array.isArray(next?.queue) ? next.queue.filter((x) => typeof x === 'string') : [],
    index: Number.isFinite(next?.index) ? next.index : 0,
    queueSource,
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

async function syncPlayerRouteSong(song) {
  if (route.name !== 'Player') return
  const current = typeof route.query.name === 'string' ? route.query.name : ''
  if (current === song) return
  try {
    await router.replace({ name: 'Player', query: { name: song } })
  } catch {}
}

function requireAuthOrOpenLogin() {
  if (isAuthed.value) return true
  openLogin()
  return false
}

function onPlayerbarOpen() {
  if (!requireAuthOrOpenLogin()) return
  openPlayer()
}

function onPlayerbarPrev() {
  if (!requireAuthOrOpenLogin()) return
  playPrev()
}

function onPlayerbarToggle() {
  if (!requireAuthOrOpenLogin()) return
  togglePlay()
}

function onPlayerbarNext() {
  if (!requireAuthOrOpenLogin()) return
  playNext()
}

function onPlayerbarFavorite(songName) {
  if (!requireAuthOrOpenLogin()) return
  toggleFavorite(songName)
}

function openQueueSource() {
  if (!requireAuthOrOpenLogin()) return
  queueSourceOpen.value = true
  queueSourceTab.value = 'mine'
  queueSourceDisplayTab.value = 'mine'
  queueSourceLoading.value = false
  queueSourceApplyError.value = ''
  queueSourceError.value = ''
  loadQueueSourceTabData('mine')
  loadQueueSourceTabData('public', { silent: true })
  loadQueueSourceTabData('favorites', { silent: true })
}

function closeQueueSource() {
  queueSourceOpen.value = false
  queueSourceLoading.value = false
  queueSourceError.value = ''
  queueSourceApplying.value = false
  queueSourceApplyError.value = ''
}

function switchQueueSourceTab(tab) {
  const next = tab === 'public' ? 'public' : 'mine'
  if (queueSourceTab.value === next) return
  queueSourceTab.value = next
  queueSourceApplyError.value = ''
  const alreadyLoaded =
    (next === 'mine' && queueSourceMinePlaylists.value.length) ||
    (next === 'public' && queueSourcePublicPlaylists.value.length)
  if (alreadyLoaded) {
    queueSourceDisplayTab.value = next
    queueSourceError.value = ''
    queueSourceLoading.value = false
    return
  }
  loadQueueSourceTabData(next, { setDisplayOnLoaded: true })
}

async function loadQueueSourceTabData(tab, options) {
  const silent = Boolean(options?.silent)
  const setDisplayOnLoaded = Boolean(options?.setDisplayOnLoaded)
  const target = tab === 'public' ? 'public' : tab === 'favorites' ? 'favorites' : 'mine'
  if (target === 'mine' && queueSourceMinePlaylists.value.length) return
  if (target === 'public' && queueSourcePublicPlaylists.value.length) return
  if (target === 'favorites' && queueSourceFavoriteSongs.value.length) return

  let loadingTimer = null
  if (!silent) {
    loadingTimer = window.setTimeout(() => {
      queueSourceLoading.value = true
    }, 150)
  }
  queueSourceError.value = ''
  try {
    if (target === 'public') {
      const { data } = await axios.get(`${apiBase}/api/playlists/public`, { params: { limit: 200 } })
      queueSourcePublicPlaylists.value = Array.isArray(data) ? data : []
    } else if (target === 'mine') {
      if (!token.value) throw new Error('NO_AUTH_MINE')
      const headers = { Authorization: `Bearer ${token.value}` }
      const { data } = await axios.get(`${apiBase}/api/playlists/username`, { headers })
      queueSourceMinePlaylists.value = Array.isArray(data) ? data : []
    } else {
      if (!token.value) throw new Error('NO_AUTH_FAV')
      const headers = { Authorization: `Bearer ${token.value}` }
      const { data } = await axios.get(`${apiBase}/api/favorites/username`, { headers })
      const arr = Array.isArray(data) ? data : []
      arr.sort((a, b) => {
        const ai = Number(a?.id)
        const bi = Number(b?.id)
        if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
        return 0
      })
      const out = []
      const seen = new Set()
      for (const f of arr) {
        const name = typeof f?.songName === 'string' ? f.songName.trim() : ''
        if (!name) continue
        if (seen.has(name)) continue
        seen.add(name)
        out.push(name)
      }
      queueSourceFavoriteSongs.value = out
    }
    if (setDisplayOnLoaded) queueSourceDisplayTab.value = target
  } catch (e) {
    if (e?.message === 'NO_AUTH_MINE') queueSourceError.value = '请先登录后查看我的歌单'
    else if (e?.message === 'NO_AUTH_FAV') queueSourceError.value = '请先登录后查看收藏夹'
    else queueSourceError.value = target === 'favorites' ? '收藏夹加载失败' : '歌单加载失败'
  } finally {
    if (loadingTimer) window.clearTimeout(loadingTimer)
    if (!silent) queueSourceLoading.value = false
  }
}

async function applyQueueFromPlaylist(p) {
  if (!p) return
  if (queueSourceApplying.value) return
  const playlistName = typeof p?.playlistName === 'string' ? p.playlistName.trim() : ''
  const username = typeof p?.username === 'string' ? p.username.trim() : ''
  if (!playlistName) return

  queueSourceApplying.value = true
  queueSourceApplyError.value = ''
  try {
    let resp
    const params = { playlistName, username }
    try {
      resp = await axios.get(`${apiBase}/api/playlist-details`, { params })
    } catch (err) {
      if (!token.value) throw err
      const headers = { Authorization: `Bearer ${token.value}` }
      resp = await axios.get(`${apiBase}/api/playlist-details`, { params, headers })
    }

    const arr = Array.isArray(resp?.data) ? resp.data : []
    arr.sort((a, b) => {
      const ai = Number(a?.id)
      const bi = Number(b?.id)
      if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
      return 0
    })

    const queue = arr.map((x) => x?.songName).filter((x) => typeof x === 'string' && x.trim())
    if (!queue.length) {
      showToast('歌单内暂无歌曲')
      return
    }

    const currentSong = playerState.value.songName
    const currentIdx = queue.findIndex((x) => x === currentSong)
    playerState.value.queue = queue
    playerState.value.index = currentIdx >= 0 ? currentIdx : 0
    playerState.value.queueSource = { type: 'playlist', playlistName, username }
    persistPlayerState()
    emitPlayerState()
    showToast(`已切换：${playlistName}`)
  } catch {
    queueSourceApplyError.value = '歌单歌曲加载失败'
  } finally {
    queueSourceApplying.value = false
  }
}

async function applyQueueFromFavorites() {
  if (queueSourceApplying.value) return
  const list = queueSourceFavoriteSongs.value
  if (!Array.isArray(list) || !list.length) {
    showToast('收藏夹暂无歌曲')
    return
  }

  queueSourceApplying.value = true
  queueSourceApplyError.value = ''
  try {
    const currentSong = playerState.value.songName
    const idx = list.findIndex((x) => x === currentSong)
    playerState.value.queue = [...list]
    playerState.value.index = idx >= 0 ? idx : 0
    playerState.value.queueSource = { type: 'favorites' }
    persistPlayerState()
    emitPlayerState()
    showToast('已切换：我的收藏')
  } finally {
    queueSourceApplying.value = false
  }
}

async function applyQueueFromFavoritesEnsured() {
  if (!token.value) {
    showToast('请先登录后查看收藏夹')
    return
  }
  if (!queueSourceFavoriteSongs.value.length) await loadQueueSourceTabData('favorites')
  await applyQueueFromFavorites()
}

let pendingPlayRetry = null

function schedulePlayRetry() {
  if (!audioEl.value) return
  if (pendingPlayRetry) return
  const el = audioEl.value
  const handler = () => {
    if (!audioEl.value) return
    pendingPlayRetry = null
    const p2 = el.play()
    if (p2 && typeof p2.then === 'function') {
      p2.then(() => {
        playerState.value.isPlaying = true
        persistPlayerState()
        emitPlayerState()
      }).catch(() => {})
    } else {
      playerState.value.isPlaying = true
      persistPlayerState()
      emitPlayerState()
    }
  }
  pendingPlayRetry = handler
  el.addEventListener('canplay', handler, { once: true })
  window.setTimeout(() => {
    if (!audioEl.value) return
    if (pendingPlayRetry !== handler) return
    pendingPlayRetry = null
    el.removeEventListener('canplay', handler)
  }, 2500)
}

function startPlayback() {
  if (!audioEl.value || !playerState.value.songName) return
  applyAudioSource(playerState.value.songName, false)

  if (audioEl.value.readyState < 2) schedulePlayRetry()

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
      schedulePlayRetry()
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

function replayCurrent() {
  const song = playerState.value.songName
  if (!song) return
  playerState.value.isPlaying = true
  playerState.value.currentTime = 0
  persistPlayerState()
  emitPlayerState()
  applyAudioSource(song, true)
  startPlayback()
}

function pickRandomIndex(len, avoid) {
  const n = Number(len)
  if (!Number.isFinite(n) || n <= 0) return 0
  const a = Number(avoid)
  if (!Number.isFinite(a) || a < 0 || a >= n) return Math.floor(Math.random() * n)
  if (n === 1) return 0
  let idx = Math.floor(Math.random() * n)
  if (idx === a) idx = (idx + 1 + Math.floor(Math.random() * (n - 1))) % n
  return idx
}

async function playPrev() {
  if (isNavigating.value) return
  isNavigating.value = true
  try {
  const q = playerState.value.queue
  if (!q.length) {
    replayCurrent()
    return
  }
  const currentIdx = q.findIndex((s) => s === playerState.value.songName)
  const baseIdx = currentIdx >= 0 ? currentIdx : 0
  const mode = playerState.value.playMode
  const nextIndex =
    mode === 'shuffle' ? pickRandomIndex(q.length, baseIdx) : (baseIdx - 1 + q.length) % q.length
  playerState.value.index = nextIndex
  playerState.value.songName = q[nextIndex]
  playerState.value.artist = ''
  playerState.value.isPlaying = true
  playerState.value.currentTime = 0
  playerState.value.audioUrl = musicUrlBySong(playerState.value.songName)
  playerState.value.lrcUrl = lrcUrlBySong(playerState.value.songName)
  playerState.value.coverUrl = coverUrlBySong(playerState.value.songName)
  persistPlayerState()
  emitPlayerState()
  loadLyrics(playerState.value.songName)
  resolveArtistIfMissing(playerState.value.songName)
  await syncPlayerRouteSong(playerState.value.songName)
  applyAudioSource(playerState.value.songName, true)
  startPlayback()
  } finally {
    isNavigating.value = false
  }
}

async function playNext() {
  if (isNavigating.value) return
  isNavigating.value = true
  try {
  const q = playerState.value.queue
  if (!q.length) {
    replayCurrent()
    return
  }
  const currentIdx = q.findIndex((s) => s === playerState.value.songName)
  const baseIdx = currentIdx >= 0 ? currentIdx : -1
  const mode = playerState.value.playMode
  const nextIndex =
    mode === 'shuffle'
      ? pickRandomIndex(q.length, baseIdx >= 0 ? baseIdx : 0)
      : (baseIdx + 1) % q.length
  playerState.value.index = nextIndex
  playerState.value.songName = q[nextIndex]
  playerState.value.artist = ''
  playerState.value.isPlaying = true
  playerState.value.currentTime = 0
  playerState.value.audioUrl = musicUrlBySong(playerState.value.songName)
  playerState.value.lrcUrl = lrcUrlBySong(playerState.value.songName)
  playerState.value.coverUrl = coverUrlBySong(playerState.value.songName)
  persistPlayerState()
  emitPlayerState()
  loadLyrics(playerState.value.songName)
  resolveArtistIfMissing(playerState.value.songName)
  await syncPlayerRouteSong(playerState.value.songName)
  applyAudioSource(playerState.value.songName, true)
  startPlayback()
  } finally {
    isNavigating.value = false
  }
}

function toggleTheme() {
  const root = document.documentElement
  root.classList.add('theme-switching')
  window.setTimeout(() => root.classList.remove('theme-switching'), 260)

  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  root.setAttribute('data-theme', theme.value)
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
  window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: token.value, user: user.value } }))
}

function logout() {
  pausePlayback()
  try {
    if (audioEl.value) audioEl.value.currentTime = 0
  } catch {}
  playerState.value.currentTime = 0
  persistPlayerState()
  emitPlayerState()

  headerKeyword.value = ''
  try {
    sessionStorage.removeItem('music_web_search_state_v1')
  } catch {}

  token.value = ''
  user.value = null
  localStorage.removeItem('auth_token')
  localStorage.removeItem('auth_user')
  loadFavorites()
  window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: '', user: null } }))

  router.push({ name: 'Discover' })
}

function openProfile() {
  router.push({ name: 'Profile' })
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
  if (!requireAuthOrOpenLogin()) return
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
  if (!requireAuthOrOpenLogin()) return
  togglePlay()
}

function onPlayerPrev() {
  if (!requireAuthOrOpenLogin()) return
  playPrev()
}

function onPlayerNext() {
  if (!requireAuthOrOpenLogin()) return
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
  if (!requireAuthOrOpenLogin()) return
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
  if (playerState.value.playMode === 'loop') {
    replayCurrent()
    return
  }
  if (playerState.value.queue.length) {
    playNext()
    return
  }
  pausePlayback()
}

function playModeText(mode) {
  if (mode === 'shuffle') return '随机播放'
  if (mode === 'loop') return '循环播放'
  return '顺序播放'
}

function togglePlayMode() {
  const cur = playerState.value.playMode
  const next = cur === 'order' ? 'shuffle' : cur === 'shuffle' ? 'loop' : 'order'
  playerState.value.playMode = next
  persistPlayerState()
  emitPlayerState()
  showToast(playModeText(next))
}

function onPlayerbarPlayMode() {
  if (!requireAuthOrOpenLogin()) return
  togglePlayMode()
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
    emitFavoritesChanged({ map: {} })
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
    emitFavoritesChanged({ map })
  } catch {
    favoriteIdBySongName.value = {}
    emitFavoritesChanged({ map: {} })
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
      emitFavoritesChanged({ songName, id: null })
      showToast('已取消收藏')
    } else {
      const { data } = await axios.post(`${apiBase}/api/favorites`, { songName }, { headers })
      const id = data?.id
      if (id != null) {
        favoriteIdBySongName.value = { ...(favoriteIdBySongName.value || {}), [songName]: id }
        emitFavoritesChanged({ songName, id })
        showToast('已收藏')
      }
    }
  } catch {}
}

function onProgressDown() {
  if (!requireAuthOrOpenLogin()) return
  isSeeking.value = true
  seekingTime.value = Number.isFinite(playerState.value.currentTime) ? playerState.value.currentTime : 0
}

function onProgressInput(e) {
  if (!requireAuthOrOpenLogin()) return
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
  if (!requireAuthOrOpenLogin()) return
  if (!isSeeking.value) return
  isSeeking.value = false
  seekTo(seekingTime.value)
}

function onCurrentLyricClick() {
  if (!requireAuthOrOpenLogin()) return
  const item = activeLyricItem.value
  if (!item) return
  if (!Number.isFinite(item.time)) return
  if (!playerState.value.songName) return
  if (lyricLoading.value) return
  if (lyricError.value) return
  seekTo(item.time)
}

function onAuthOpen(e) {
  const mode = typeof e?.detail?.mode === 'string' ? e.detail.mode : 'login'
  if (mode === 'register') openRegister()
  else openLogin()
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
  window.addEventListener('auth:open', onAuthOpen)
  window.addEventListener('favorites:changed', onFavoritesChanged)
  window.addEventListener('toast:show', onToastShow)

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
  window.removeEventListener('auth:open', onAuthOpen)
  window.removeEventListener('favorites:changed', onFavoritesChanged)
  window.removeEventListener('toast:show', onToastShow)
  if (toastTimer) window.clearTimeout(toastTimer)
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
          <span class="brand-name">LYY-Music</span>
        </div>
        <div class="topbar-search">
          <input
            v-model="headerKeyword"
            class="input"
            type="text"
            placeholder="搜索歌曲 / 歌手"
            @keyup.enter="onHeaderSearchEnter"
          />
        </div>
      </div>

      <div class="topbar-right">
        <template v-if="!isAuthed">
          <button class="btn ghost login-rotate" type="button" @click="openLogin">登录</button>
          <button class="btn primary" type="button" @click="openRegister">注册</button>
        </template>

        <div v-if="isAuthed" class="user">
          <div class="user-name">{{ user?.username || '已登录' }}</div>
          <img class="avatar" :src="userAvatarUrl" alt="User" />
          <div class="user-menu">
            <div class="menu-card">
              <button class="menu-btn" type="button" @click="openProfile">个人中心</button>
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

    <div class="toast-bar" :class="{ active: toastOpen }" v-if="toastMessage">
      <div class="toast-card">{{ toastMessage }}</div>
    </div>

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
            >我的歌单</a
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
        <div class="playerbar-left" @click="onPlayerbarOpen">
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
              :disabled="isAuthed && !playerState.songName"
              @click="onPlayerbarPrev"
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
              :disabled="isAuthed && !playerState.songName"
              @click="onPlayerbarToggle"
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
              :disabled="isAuthed && !playerState.songName"
              @click="onPlayerbarNext"
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
              :disabled="isAuthed && !playerState.songName"
              :class="{ active: isFavorited(playerState.songName) }"
              @click="onPlayerbarFavorite(playerState.songName)"
            >
              <svg v-if="isFavorited(playerState.songName)" width="18" height="18" viewBox="0 0 24 24">
                <path
                  d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.62L12 2 9.19 8.62 2 9.24l5.46 4.73L5.82 21z"
                  fill="currentColor"
                />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.62L12 2 9.19 8.62 2 9.24l5.46 4.73L5.82 21z"
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
              :disabled="isAuthed && !playerState.songName"
              @click="onPlayerbarPlayMode"
            >
              <svg v-if="playerState.playMode === 'order'" width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M7 6h14M7 12h10M7 18h6"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M3 6h.01M3 12h.01M3 18h.01"
                  stroke="currentColor"
                  stroke-width="3"
                  stroke-linecap="round"
                />
              </svg>
              <svg v-else-if="playerState.playMode === 'shuffle'" width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M16 3h5v5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M4 4l7 7"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
                <path
                  d="M4 20l7-7"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
                <path
                  d="M16 3c3 0 5 2 5 5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
                <path
                  d="M16 21h5v-5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M16 21c3 0 5-2 5-5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M17 2l4 4-4 4"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M3 11a8 8 0 0 1 14-5h4"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M7 22l-4-4 4-4"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M21 13a8 8 0 0 1-14 5H3"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>

            <button class="playerbar-btn" type="button" @click="openQueueSource">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M4 6h16M4 12h16M4 18h10"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M18 18l2 2 4-4"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  transform="translate(-4 0)"
                />
              </svg>
            </button>
          </div>
        </div>

        <div class="playerbar-right">
          <div class="playerbar-progress">
              <input
                class="playerbar-range"
                :class="{ seeking: isSeeking }"
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

  <div v-if="authOpen" class="modal-mask" :class="{ active: authOpen }">
    <div class="modal">
      <div class="modal-head">
        <div class="modal-title">{{ authMode === 'login' ? '登录' : '注册' }}</div>
        <button class="modal-close auth-close" type="button" @click="closeAuth" aria-label="关闭">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div v-if="authMode === 'login'" class="form" @keydown.enter.prevent="submitLogin">
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

      <div v-else class="form" @keydown.enter.prevent="submitRegister">
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

  <div v-if="queueSourceOpen" class="modal-mask" :class="{ active: queueSourceOpen }" @click.self="closeQueueSource">
    <div class="modal queue-source-modal">
      <div class="modal-head">
        <div class="modal-title">切换下一首/上一首歌单来源</div>
        <button class="modal-close" type="button" @click="closeQueueSource" aria-label="关闭">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="queue-source-tabs">
        <button
          class="queue-source-tab"
          :class="{ active: queueSourceTab === 'mine' }"
          type="button"
          :disabled="queueSourceLoading || queueSourceApplying"
          @click="switchQueueSourceTab('mine')"
        >
          我的收藏/歌单
        </button>
        <button
          class="queue-source-tab"
          :class="{ active: queueSourceTab === 'public' }"
          type="button"
          :disabled="queueSourceLoading || queueSourceApplying"
          @click="switchQueueSourceTab('public')"
        >
          公开歌单
        </button>
      </div>

      <div class="queue-source-body">
        <div v-if="queueSourceLoading && !queueSourceHasDisplayedContent" class="queue-source-hint">加载中...</div>
        <div v-else-if="queueSourceError && !queueSourceHasDisplayedContent" class="queue-source-hint">{{ queueSourceError }}</div>

        <template v-else>
          <div v-if="queueSourceDisplayTab === 'public' && !queueSourceDisplayedPlaylists.length" class="queue-source-hint">暂无歌单</div>
          <template v-else>
            <div class="queue-source-list">
              <button
                v-if="queueSourceDisplayTab === 'mine'"
                class="queue-source-item"
                :class="{ active: isQueueSourceFavoritesActive }"
                type="button"
                :disabled="queueSourceApplying"
                @click="applyQueueFromFavoritesEnsured"
              >
                <div class="queue-source-item-name">收藏夹</div>
                <div class="queue-source-item-sub">
                  <span v-if="queueSourceFavoriteSongs.length">共 {{ queueSourceFavoriteSongs.length }} 首</span>
                  <span v-else>暂无歌曲</span>
                </div>
              </button>

              <button
                v-for="p in queueSourceDisplayedPlaylists"
                :key="`${p?.playlistName || ''}-${p?.username || ''}-${p?.id || ''}`"
                class="queue-source-item"
                :class="{ active: isQueueSourcePlaylistActive(p) }"
                type="button"
                :disabled="queueSourceApplying"
                @click="applyQueueFromPlaylist(p)"
              >
                <div class="queue-source-item-name">{{ p?.playlistName }}</div>
                <div class="queue-source-item-sub">
                  <span class="queue-source-item-owner">{{ p?.username }}</span>
                  <span class="queue-source-item-dot">·</span>
                  <span class="queue-source-item-flag">{{ p?.isPublic ? '公开' : '私密' }}</span>
                </div>
              </button>
            </div>

            <div v-if="queueSourceDisplayTab === 'mine' && !queueSourceDisplayedPlaylists.length" class="queue-source-hint">
              暂无歌单
            </div>
          </template>
        </template>

        <div class="form-error" v-if="queueSourceApplyError">{{ queueSourceApplyError }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  height: 100vh;
  min-height: 0;
  background: transparent;
  color: var(--text);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topbar {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 clamp(16px, 4vw, 64px);
  border-bottom: 1px solid var(--border);
  background: var(--panel-glass);
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--shadow);
}

.toast-bar {
  position: fixed;
  top: 70px;
  left: 50%;
  transform: translate(-50%, -10px);
  opacity: 0;
  pointer-events: none;
  transition: var(--transition-fast);
  z-index: 200;
  padding: 12px 16px;
  width: min(520px, calc(100vw - 32px));
}

.toast-bar.active {
  opacity: 1;
  transform: translate(-50%, 10px);
}

.toast-card {
  width: 100%;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--panel) 92%, transparent);
  backdrop-filter: blur(18px);
  box-shadow: var(--shadow-hover);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
  padding: 12px 14px;
  text-align: center;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 520px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
  transition: var(--transition);
}

.brand:hover {
  transform: scale(1.05);
}

.brand-logo {
  height: 32px;
  width: auto;
  display: block;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}

.brand-name {
  font-weight: 900;
  letter-spacing: 0.6px;
  font-size: 18px;
  line-height: 1;
  background: linear-gradient(90deg, #ff4e4e, #ffb199, #7c6cff, #ff4e4e);
  background-size: 260% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 12px 28px rgba(255, 78, 78, 0.14);
  animation: brandShimmer 5s linear infinite;
}

@media (max-width: 980px) {
  .brand-name {
    display: none;
  }
}

@keyframes brandShimmer {
  0% {
    background-position: 0% 50%;
  }
  100% {
    background-position: 100% 50%;
  }
}

.topbar-search {
  flex: 1;
  max-width: 400px;
}

.input {
  width: 100%;
  height: 42px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  padding: 0 20px;
  outline: none;
  transition: var(--transition);
  font-size: 14px;
}

.input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--border-focus);
}

.input::placeholder {
  color: var(--text-muted);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn {
  height: 38px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  padding: 0 16px;
  cursor: pointer;
  transition: var(--transition);
  font-weight: 500;
  font-size: 14px;
}

.btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
}

.btn.login-rotate {
  transform-origin: center;
}


.btn.login-rotate:hover {
  transform: translateY(-1px);
}

.btn.primary {
  background: var(--accent-gradient);
  color: white;
  border-color: transparent;
}

.btn.primary:hover {
  background: linear-gradient(135deg, var(--accent-hover) 0%, #ff8585 100%);
}

.btn.ghost {
  background: color-mix(in srgb, var(--accent) 10%, var(--card));
  border-color: color-mix(in srgb, var(--accent) 18%, var(--border));
  color: var(--text);
}

.btn.ghost:hover {
  background: color-mix(in srgb, var(--accent) 14%, var(--card));
  border-color: color-mix(in srgb, var(--accent) 28%, var(--border));
  color: var(--text);
}

.icon-btn {
  height: 38px;
  width: 38px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--transition);
}

.icon-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-1px);
}

.user {
  position: relative;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 0 12px;
  border-radius: var(--radius);
  transition: var(--transition);
}

.user:hover {
  background: var(--card-hover);
}

.user-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.avatar {
  height: 32px;
  width: 32px;
  border-radius: var(--radius-full);
  border: 2px solid var(--border);
  display: block;
  object-fit: cover;
  transition: var(--transition);
}

.user:hover .avatar {
  border-color: var(--accent);
}

.user-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 2px);
  display: none;
  z-index: 1000;
}

.user:hover .user-menu {
  display: block;
  animation: fadeIn 0.2s ease-out;
}

.menu-card {
  width: 240px;
  padding: 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-hover);
  backdrop-filter: blur(20px);
}

.menu-btn {
  width: 100%;
  height: 40px;
  border-radius: var(--radius);
  border: 1px solid transparent;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  transition: var(--transition);
  padding: 0 12px;
  text-align: left;
  font-size: 14px;
}

.menu-btn:hover {
  background: var(--bg-secondary);
  border-color: var(--accent);
  color: var(--accent);
}

.main {
  flex: 1;
  display: flex;
  min-height: 0;
  padding: 0 clamp(12px, 3.2vw, 40px);
}

.sidebar {
  width: 240px;
  border-right: 1px solid var(--border);
  padding: 20px 16px;
  background: var(--panel-glass);
  display: flex;
  flex-direction: column;
  gap: 24px;
  backdrop-filter: blur(18px);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 16px;
  border-radius: var(--radius);
  color: var(--text);
  text-decoration: none;
  background: transparent;
  border: 1px solid transparent;
  transition: var(--transition);
  font-weight: 500;
  font-size: 14px;
}

.nav-item:hover {
  background: var(--card-hover);
  border-color: var(--border);
  transform: translateX(4px);
}

.nav-item.active {
  background: linear-gradient(135deg, 
    color-mix(in srgb, var(--accent) 15%, transparent) 0%,
    color-mix(in srgb, var(--accent) 5%, transparent) 100%);
  border-color: var(--accent);
  color: var(--text);
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.2);
}

.nav-item.nav-parent {
  justify-content: space-between;
}

.nav-item.nav-parent .caret {
  font-size: 12px;
  color: var(--text-muted);
  transition: var(--transition);
}

.nav-item.nav-parent:hover .caret {
  transform: rotate(90deg);
}

.nav-sub {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 12px;
  margin-top: -2px;
}

.nav-item.sub {
  height: 38px;
  font-size: 13px;
  opacity: 0.95;
  color: var(--text-secondary);
}

.nav-item.sub:hover {
  color: var(--text);
}

.content {
  flex: 1;
  padding: 24px 24px 0;
  overflow: auto;
  min-height: 0;
  min-width: 0;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--bg) 72%, transparent) 0%,
    color-mix(in srgb, var(--bg-secondary) 72%, transparent) 100%
  );
}

.playerbar {
  height: 100px;
  background: rgba(var(--panel), 0.95);
  border-top: 1px solid var(--border);
  backdrop-filter: blur(20px);
}

.playerbar-inner {
  height: 100%;
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto minmax(300px, 1fr);
  align-items: center;
  gap: 20px;
  padding: 0 64px;
  max-width: 1200px;
  margin: 0 auto;
}

.playerbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
  cursor: pointer;
  transition: var(--transition);
}

.playerbar-left:hover {
  transform: translateX(4px);
}

.playerbar-cover {
  height: 56px;
  width: 56px;
  border-radius: var(--radius);
  border: 2px solid var(--border);
  object-fit: cover;
  flex: none;
  transition: var(--transition);
  box-shadow: var(--shadow-card);
}

.playerbar-left:hover .playerbar-cover {
  border-color: var(--accent);
  transform: scale(1.05);
}

.playerbar-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.playerbar-title {
  font-weight: 600;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text);
}

.playerbar-sub {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playerbar-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.playerbar-current-lyric {
  height: 44px;
  width: 360px;
  max-width: calc(34vw + 40px);
  margin-left: -40px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  padding: 0 16px;
  display: flex;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  user-select: none;
  transition: var(--transition);
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
  gap: 12px;
}

.playerbar-btn {
  height: 44px;
  width: 44px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--transition);
  font-size: 16px;
}

.playerbar-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover);
}

.playerbar-btn.primary {
  background: var(--accent-gradient);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.3);
}

.playerbar-btn.primary:hover {
  background: linear-gradient(135deg, var(--accent-hover) 0%, #ff8585 100%);
  box-shadow: 0 6px 16px rgba(255, 78, 78, 0.4);
}

.playerbar-btn.active {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}

.playerbar-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.playerbar-right {
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: center;
  padding: 8px 0;
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
  height: 6px;
  accent-color: var(--accent);
  background: var(--border);
  border-radius: var(--radius-full);
  outline: none;
  cursor: pointer;
  transition: var(--transition);
}

.playerbar-range:focus,
.playerbar-range:focus-visible {
  outline: none;
  box-shadow: none;
}

.playerbar-range.seeking {
  background: color-mix(in srgb, var(--accent) 35%, var(--border));
  box-shadow: 0 0 0 3px var(--border-focus);
}

.playerbar-range::-webkit-slider-thumb {
  appearance: none;
  height: 16px;
  width: 16px;
  border-radius: var(--radius-full);
  background: var(--accent-gradient);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(255, 78, 78, 0.4);
  transition: var(--transition);
}

@media (hover: hover) and (pointer: fine) {
  .playerbar-range::-webkit-slider-thumb:hover {
    transform: scale(1.2);
    box-shadow: 0 4px 12px rgba(255, 78, 78, 0.6);
  }
}

.playerbar-time {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
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
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(20px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  opacity: 0;
  visibility: hidden;
  transition: var(--transition);
}

.modal-mask.active {
  opacity: 1;
  visibility: visible;
}

.modal {
  width: 420px;
  max-width: 100%;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-hover);
  padding: 24px;
  transform: scale(0.9);
  transition: var(--transition);
}

.modal-mask.active .modal {
  transform: scale(1);
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.modal-title {
  font-weight: 600;
  font-size: 18px;
  color: var(--text);
}

.modal-close {
  height: 38px;
  width: 38px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  font-size: 0;
  line-height: 1;
  transition: var(--transition);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.modal-close svg {
  display: block;
}

.modal-close:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: rotate(90deg);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field .label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  font-weight: 500;
}

.field-input {
  width: 100%;
  height: 44px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 16px;
  outline: none;
  transition: var(--transition);
  font-size: 14px;
}

.field-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--border-focus);
}

.form-error {
  color: #ff6b6b;
  font-size: 12px;
  margin-top: 4px;
}

.submit-btn {
  height: 44px;
  border-radius: var(--radius);
  border: 1px solid transparent;
  background: var(--accent-gradient);
  color: #fff;
  cursor: pointer;
  transition: var(--transition);
  font-weight: 500;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.3);
}

.submit-btn:hover {
  background: linear-gradient(135deg, var(--accent-hover) 0%, #ff8585 100%);
  box-shadow: 0 6px 16px rgba(255, 78, 78, 0.4);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.switch-btn {
  height: 44px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text);
  cursor: pointer;
  transition: var(--transition);
  font-weight: 500;
  font-size: 14px;
}

.switch-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 5%, transparent);
}

.queue-source-modal {
  width: 520px;
}

.queue-source-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
}

.queue-source-tab {
  flex: 1;
  height: 36px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
  font-weight: 700;
  font-size: 13px;
}

.queue-source-tab.active {
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
  color: var(--text);
}

.queue-source-tab:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.queue-source-body {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.queue-source-hint {
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text-secondary);
  padding: 14px;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

.queue-source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 340px;
  overflow: auto;
  padding-right: 4px;
}

.queue-source-item {
  text-align: left;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
  padding: 12px 12px;
  cursor: pointer;
  transition: var(--transition);
  color: var(--text);
}

.queue-source-item.active {
  border-color: color-mix(in srgb, var(--accent) 70%, var(--border));
  background: color-mix(in srgb, var(--accent) 10%, var(--panel));
  box-shadow: var(--shadow-hover);
}

.queue-source-item:hover {
  border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
  background: color-mix(in srgb, var(--accent) 6%, var(--panel));
}

.queue-source-item:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.queue-source-item-name {
  font-weight: 900;
  font-size: 14px;
  line-height: 1.2;
}

.queue-source-item-sub {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.queue-source-item-dot {
  opacity: 0.6;
}
</style>
