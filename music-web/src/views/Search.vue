<script setup>
import axios from 'axios'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import OverlayMask from '../components/OverlayMask.vue'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultSongCover = 'https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC'
const SEARCH_STATE_KEY = 'music_web_search_state_v1'

function safeParseJson(value) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return defaultSongCover
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

const hotTags = [
  '马克西姆',
  '克罗地亚狂想曲',
  '林俊杰',
  '陈奕迅',
  '周传雄',
  'Counting Stars',
  '卡农',
]

const keyword = ref('')
const loading = ref(false)
const error = ref('')
const results = ref([])
const playlists = ref([])
const playlistsLoaded = ref(false)
const favoritesLoaded = ref(false)
const favoriteIdBySongName = ref({})

const addModalOpen = ref(false)
const addModalSongName = ref('')
const addModalSelectedPlaylists = ref([])
const addModalSubmitting = ref(false)
const addModalError = ref('')

function loadSearchState() {
  try {
    const raw = sessionStorage.getItem(SEARCH_STATE_KEY) || ''
    const parsed = safeParseJson(raw)
    if (!parsed || typeof parsed !== 'object') return null
    if (typeof parsed.keyword !== 'string' || !parsed.keyword.trim()) return null
    return parsed
  } catch {
    return null
  }
}

function saveSearchState() {
  try {
    const key = keyword.value.trim()
    if (!key) return
    sessionStorage.setItem(
      SEARCH_STATE_KEY,
      JSON.stringify({
        keyword: key,
        results: Array.isArray(results.value) ? results.value : [],
        error: error.value || '',
        favoriteIdBySongName: favoriteIdBySongName.value || {},
        favoritesLoaded: Boolean(favoritesLoaded.value),
        ts: Date.now(),
      }),
    )
  } catch {}
}

function clearSearchState(options) {
  try {
    sessionStorage.removeItem(SEARCH_STATE_KEY)
  } catch {}
  keyword.value = ''
  results.value = []
  error.value = ''

  const clearRoute = options?.clearRoute !== false
  if (!clearRoute) return
  if (route.name !== 'Search') return
  if (!route.query.q) return
  router.replace({ name: 'Search', query: {} }).catch(() => {})
}

function emitFavoritesChanged(detail) {
  window.dispatchEvent(new CustomEvent('favorites:changed', { detail: { ...(detail || {}), source: 'search' } }))
}

function onFavoritesChanged(e) {
  const detail = e?.detail || {}
  if (detail?.source === 'search') return
  if (detail && typeof detail === 'object' && detail.map && typeof detail.map === 'object') {
    favoriteIdBySongName.value = detail.map
    favoritesLoaded.value = true
    return
  }
  const songName = typeof detail?.songName === 'string' ? detail.songName : ''
  if (!songName) return
  const id = detail?.id
  const next = { ...(favoriteIdBySongName.value || {}) }
  if (id == null) delete next[songName]
  else next[songName] = id
  favoriteIdBySongName.value = next
  favoritesLoaded.value = true
  saveSearchState()
}

function onAuthChanged(e) {
  const nextToken = typeof e?.detail?.token === 'string' ? e.detail.token : ''
  if (nextToken) return
  clearSearchState({ clearRoute: true })
}

watch(
  () => route.query.q,
  async (q) => {
    const nextQ = typeof q === 'string' ? q.trim() : ''
    if (nextQ) {
      keyword.value = nextQ
      await doSearch()
      return
    }

    const saved = loadSearchState()
    if (saved) {
      keyword.value = saved.keyword
      results.value = Array.isArray(saved.results) ? saved.results : []
      error.value = typeof saved.error === 'string' ? saved.error : ''
      if (saved.favoriteIdBySongName && typeof saved.favoriteIdBySongName === 'object') {
        favoriteIdBySongName.value = saved.favoriteIdBySongName
        favoritesLoaded.value = Boolean(saved.favoritesLoaded)
      }
      return
    }

    keyword.value = ''
    results.value = []
    error.value = ''
  },
  { immediate: true },
)

function pickErrorMessage(err) {
  const message = err?.response?.data?.message
  if (typeof message === 'string' && message.trim()) return message
  return '请求失败'
}

function getAuthToken() {
  return localStorage.getItem('auth_token') || ''
}

function requestLogin() {
  window.dispatchEvent(new CustomEvent('auth:open', { detail: { mode: 'login' } }))
}

function showToast(message) {
  window.dispatchEvent(new CustomEvent('toast:show', { detail: { message } }))
}

function onEnter() {
  const key = keyword.value.trim()
  if (!key) return
  router.push(`/search?q=${encodeURIComponent(key)}`)
}

function selectHotTag(tag) {
  keyword.value = tag
  onEnter()
}

function dispatchPlayerSet(songName, artist) {
  const queue = results.value.map((x) => x?.songName).filter((x) => typeof x === 'string' && x.trim())
  const index = queue.findIndex((x) => x === songName)
  window.dispatchEvent(
    new CustomEvent('player:set', {
      detail: {
        songName,
        artist: typeof artist === 'string' ? artist : '',
        queue,
        index: index >= 0 ? index : 0,
        isPlaying: true,
      },
    }),
  )
}

function playSong(songName, artist) {
  goPlayer(songName, artist)
}

function goPlayer(songName, artist) {
  dispatchPlayerSet(songName, artist)
  router.push(`/player?name=${encodeURIComponent(songName)}`)
}

async function loadPlaylists() {
  const authToken = getAuthToken()
  if (!authToken) {
    playlists.value = []
    playlistsLoaded.value = false
    return
  }

  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    const { data } = await axios.get(`${apiBase}/api/playlists/username`, { headers })
    playlists.value = Array.isArray(data) ? data : []
    playlistsLoaded.value = true
  } catch {
    playlists.value = []
    playlistsLoaded.value = false
  }
}

async function loadFavorites() {
  const authToken = getAuthToken()
  if (!authToken) {
    favoriteIdBySongName.value = {}
    favoritesLoaded.value = false
    emitFavoritesChanged({ map: {} })
    return
  }

  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    const { data } = await axios.get(`${apiBase}/api/favorites/username`, { headers })
    const arr = Array.isArray(data) ? data : []
    const map = {}
    for (const f of arr) {
      if (!f || !f.songName) continue
      map[f.songName] = f.id
    }
    favoriteIdBySongName.value = map
    favoritesLoaded.value = true
    emitFavoritesChanged({ map })
  } catch {
    favoriteIdBySongName.value = {}
    favoritesLoaded.value = false
    emitFavoritesChanged({ map: {} })
  }
}

function isFavorited(songName) {
  return Boolean(favoriteIdBySongName.value?.[songName])
}

async function toggleFavorite(songName) {
  const authToken = getAuthToken()
  if (!authToken) {
    showToast('请先登录')
    requestLogin()
    return
  }
  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    const existingId = favoriteIdBySongName.value?.[songName]
    if (existingId) {
      await axios.delete(`${apiBase}/api/favorites/${existingId}`, { headers })
      const next = { ...(favoriteIdBySongName.value || {}) }
      delete next[songName]
      favoriteIdBySongName.value = next
      emitFavoritesChanged({ songName, id: null })
      showToast('已取消收藏')
      saveSearchState()
    } else {
      const { data } = await axios.post(`${apiBase}/api/favorites`, { songName }, { headers })
      const id = data?.id
      if (id != null) {
        favoriteIdBySongName.value = { ...(favoriteIdBySongName.value || {}), [songName]: id }
        emitFavoritesChanged({ songName, id })
      }
      showToast('已收藏')
      saveSearchState()
    }
  } catch (err) {
    showToast(pickErrorMessage(err))
  }
}

async function openAddToPlaylistModal(songName) {
  const authToken = getAuthToken()
  if (!authToken) {
    showToast('请先登录')
    requestLogin()
    return
  }

  addModalError.value = ''
  if (!playlistsLoaded.value) await loadPlaylists()
  addModalSongName.value = typeof songName === 'string' ? songName : ''
  addModalSelectedPlaylists.value = []
  addModalOpen.value = true
}

function closeAddModal() {
  addModalOpen.value = false
  addModalSubmitting.value = false
  addModalError.value = ''
  addModalSongName.value = ''
  addModalSelectedPlaylists.value = []
}

async function submitAddToPlaylists() {
  const authToken = getAuthToken()
  if (!authToken) {
    showToast('请先登录')
    closeAddModal()
    requestLogin()
    return
  }

  const songName = addModalSongName.value
  if (!songName) {
    closeAddModal()
    return
  }

  const picked = Array.isArray(addModalSelectedPlaylists.value) ? addModalSelectedPlaylists.value : []
  const playlistNames = Array.from(new Set(picked.map((x) => (typeof x === 'string' ? x.trim() : '')).filter(Boolean)))

  if (!playlistNames.length) {
    addModalError.value = '请选择至少一个歌单'
    return
  }

  addModalSubmitting.value = true
  addModalError.value = ''
  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    const settled = await Promise.allSettled(
      playlistNames.map((playlistName) =>
        axios.post(
          `${apiBase}/api/playlist-details`,
          { playlistName, songName },
          { headers },
        ),
      ),
    )

    const successCount = settled.filter((x) => x.status === 'fulfilled').length
    const firstReject = settled.find((x) => x.status === 'rejected')

    if (successCount > 0) {
      showToast(successCount === 1 ? '已加入歌单' : `已加入 ${successCount} 个歌单`)
      closeAddModal()
      return
    }

    if (firstReject?.status === 'rejected') {
      addModalError.value = pickErrorMessage(firstReject.reason)
      return
    }

    addModalError.value = '加入失败'
  } finally {
    addModalSubmitting.value = false
  }
}

async function doSearch() {
  const key = keyword.value.trim()
  if (!key) {
    results.value = []
    error.value = ''
    return
  }

  const authToken = getAuthToken()
  if (!authToken) {
    results.value = []
    error.value = '请先登录后再搜索'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    if (!playlistsLoaded.value) await loadPlaylists()
    if (!favoritesLoaded.value) await loadFavorites()
    const [songResp, artistResp] = await Promise.all([
      axios.get(`${apiBase}/api/meta/song-name-like`, {
        params: { songName: key },
        headers,
      }),
      axios.get(`${apiBase}/api/meta/artist-name`, {
        params: { artistName: key },
        headers,
      }),
    ])

    const merged = new Map()
    const addOne = (item) => {
      if (!item) return
      const id = item.id ?? `${item.songName || ''}__${item.artist || ''}`
      merged.set(id, item)
    }

    const songList = Array.isArray(songResp.data) ? songResp.data : []
    const artistList = Array.isArray(artistResp.data) ? artistResp.data : []
    songList.forEach(addOne)
    artistList.forEach(addOne)

    results.value = Array.from(merged.values())
  } catch (err) {
    results.value = []
    error.value = pickErrorMessage(err)
  } finally {
    loading.value = false
    saveSearchState()
  }
}

onMounted(() => {
  window.addEventListener('favorites:changed', onFavoritesChanged)
  window.addEventListener('auth:changed', onAuthChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('favorites:changed', onFavoritesChanged)
  window.removeEventListener('auth:changed', onAuthChanged)
})
</script>

<template>
  <div class="page animate-fade-in">
    <div class="card">
      <div class="title">热门搜索</div>
      <div class="tags">
        <button
          v-for="(t, index) in hotTags"
          :key="t"
          class="tag hover-lift"
          type="button"
          @click="selectHotTag(t)"
          :style="{ animationDelay: `${index * 0.05}s` }"
        >
          {{ t }}
        </button>
      </div>

      <div class="search-row">
        <input
          v-model="keyword"
          class="input"
          placeholder="输入关键词，回车搜索"
          @keyup.enter="onEnter"
        />
      </div>
    </div>

    <div class="list card">
      <div class="title">
        搜索结果
        <span class="sub" v-if="keyword">“{{ keyword }}”</span>
      </div>

      <div class="items">
        <div class="empty" v-if="loading">加载中...</div>
        <div class="empty" v-else-if="error">{{ error }}</div>
        <div class="empty" v-else-if="keyword && results.length === 0">暂无结果</div>

        <div v-for="(s, idx) in results" :key="s.id ?? `${s.songName}-${idx}`" class="item hover-lift" :style="{ animationDelay: `${idx * 0.05}s` }">
          <img
            class="cover"
            :src="coverUrlBySong(s.songName)"
            alt="cover"
            role="button"
            tabindex="0"
            @click="playSong(s.songName, s.artist)"
            @keydown.enter.prevent="playSong(s.songName, s.artist)"
            @keydown.space.prevent="playSong(s.songName, s.artist)"
          />
          <div class="meta">
            <div class="name">{{ s.songName }}</div>
            <div class="artist">{{ s.artist }}</div>
          </div>
          <div class="actions">
            <button class="icon-action fav" type="button" @click="toggleFavorite(s.songName)">
              <svg v-if="isFavorited(s.songName)" width="18" height="18" viewBox="0 0 24 24">
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
            <button class="action-btn" type="button" @click="openAddToPlaylistModal(s.songName)">
              加入歌单
            </button>
            <button class="play" type="button" @click="playSong(s.songName, s.artist)">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M8 5v14l12-7L8 5Z" fill="currentColor" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <OverlayMask v-model:visible="addModalOpen">
      <div class="add-modal">
        <div class="add-modal-head">
          <div class="add-modal-title">加入歌单</div>
          <button class="modal-close" type="button" @click="closeAddModal" aria-label="关闭">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
        </div>

        <div class="add-modal-sub" v-if="addModalSongName">歌曲：{{ addModalSongName }}</div>

        <div class="add-modal-body">
          <div v-if="!playlists.length" class="add-empty">
            暂无歌单，请先去“歌单”页面创建
          </div>
          <div v-else class="playlist-checks">
            <label v-for="p in playlists" :key="p.id ?? p.playlistName" class="check-item">
              <input
                class="checkbox"
                type="checkbox"
                :value="p.playlistName"
                v-model="addModalSelectedPlaylists"
              />
              <span class="check-text">{{ p.playlistName }}</span>
            </label>
          </div>
        </div>

        <div class="form-error" v-if="addModalError">{{ addModalError }}</div>

        <div class="add-modal-actions">
          <button class="btn-lite" type="button" :disabled="addModalSubmitting" @click="closeAddModal">取消</button>
          <button class="btn-primary" type="button" :disabled="addModalSubmitting" @click="submitAddToPlaylists">
            {{ addModalSubmitting ? '加入中...' : '确认加入' }}
          </button>
        </div>
      </div>
    </OverlayMask>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: 20px;
  transition: var(--transition);
}

.card:hover {
  box-shadow: var(--shadow-hover);
}

.title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text);
}

.sub {
  color: var(--text-secondary);
  font-weight: 400;
  margin-left: 8px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.tag {
  border-radius: var(--radius-full);
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text);
  height: 36px;
  padding: 0 16px;
  cursor: pointer;
  transition: var(--transition);
  font-size: 14px;
  font-weight: 500;
}

.tag:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 5%, transparent);
  transform: translateY(-1px);
}

.search-row {
  display: flex;
  gap: 12px;
}

.input {
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

.input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--border-focus);
}

.input::placeholder {
  color: var(--text-muted);
}

.items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty {
  color: var(--text-muted);
  font-size: 14px;
  padding: 16px 4px;
  text-align: center;
  font-weight: 500;
}

.item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
  transition: var(--transition);
}

.item:hover {
  border-color: var(--accent);
  background: var(--card-hover);
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
}

.cover {
  width: 60px;
  height: 60px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  object-fit: cover;
  transition: var(--transition);
  cursor: pointer;
}

.item:hover .cover {
  transform: scale(1.05);
}

.meta {
  flex: 1;
  min-width: 0;
}

.name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
  color: var(--text);
}

.artist {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
  font-weight: 500;
}

.play {
  height: 40px;
  width: 40px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--accent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--transition);
  font-size: 16px;
}

.play:hover {
  border-color: var(--accent);
  background: var(--accent-gradient);
  color: #fff;
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.3);
}

.icon-action {
  height: 40px;
  width: 40px;
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

.icon-action:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: scale(1.1);
  box-shadow: var(--shadow-hover);
}

.icon-action.fav {
  color: var(--accent);
}

.icon-action.fav:hover {
  background: var(--accent-gradient);
  color: #fff;
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.3);
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-btn {
  height: 36px;
  padding: 0 12px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  cursor: pointer;
  white-space: nowrap;
  transition: var(--transition);
  font-size: 14px;
  font-weight: 500;
}

.action-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
}

.add-modal {
  width: min(520px, calc(100vw - 40px));
  padding: 18px 18px 16px;
}

.add-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.add-modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
}

.modal-close {
  height: 34px;
  width: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  font-size: 0;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.modal-close svg {
  display: block;
}

.add-modal-sub {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 12px;
  font-weight: 500;
}

.add-modal-body {
  border: 1px solid var(--border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--panel) 88%, transparent);
  padding: 12px;
  max-height: min(52vh, 340px);
  overflow: auto;
}

.add-empty {
  padding: 10px 6px;
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
  font-weight: 500;
}

.playlist-checks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  transition: var(--transition);
  cursor: pointer;
}

.check-item:hover {
  border-color: var(--accent);
  background: var(--card-hover);
}

.checkbox {
  height: 16px;
  width: 16px;
  accent-color: var(--accent);
}

.check-text {
  font-size: 14px;
  color: var(--text);
  font-weight: 600;
}

.form-error {
  margin-top: 10px;
  color: #d44;
  font-size: 12px;
  font-weight: 600;
}

.add-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}

.btn-lite {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  transition: var(--transition);
  font-weight: 600;
}

.btn-lite:hover {
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: var(--shadow-hover);
}

.btn-primary {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: var(--accent-gradient);
  color: #fff;
  cursor: pointer;
  transition: var(--transition);
  font-weight: 700;
}

.btn-primary:disabled,
.btn-lite:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
