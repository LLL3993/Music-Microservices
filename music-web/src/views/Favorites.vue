<script setup>
import axios from 'axios'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultSongCover = 'https://dummyimage.com/120x120/999999/ff4400.png&text=MUSIC'

const loading = ref(false)
const error = ref('')
const items = ref([])
const artistBySongName = ref({})

function showToast(message) {
  window.dispatchEvent(new CustomEvent('toast:show', { detail: { message } }))
}

function emitFavoritesChanged(detail) {
  window.dispatchEvent(new CustomEvent('favorites:changed', { detail: { ...(detail || {}), source: 'favorites' } }))
}

function onFavoritesChanged(e) {
  const detail = e?.detail || {}
  if (detail?.source === 'favorites') return
  if (detail && typeof detail === 'object' && detail.map && typeof detail.map === 'object') {
    const map = detail.map || {}
    const next = []
    for (const [songName, id] of Object.entries(map)) {
      if (!songName) continue
      next.push({ id, songName })
    }
    items.value = next
    return
  }

  const songName = typeof detail?.songName === 'string' ? detail.songName : ''
  if (!songName) return
  const id = detail?.id
  if (id == null) {
    items.value = items.value.filter((x) => x?.songName !== songName)
    const nextArtist = { ...(artistBySongName.value || {}) }
    delete nextArtist[songName]
    artistBySongName.value = nextArtist
    return
  }
  if (items.value.some((x) => x?.songName === songName)) return
  items.value = [{ id, songName }, ...items.value]
  loadArtists(items.value)
}

function pickErrorMessage(err) {
  const message = err?.response?.data?.message
  if (typeof message === 'string' && message.trim()) return message
  return '请求失败'
}

function getAuthHeader() {
  const token = localStorage.getItem('auth_token') || ''
  if (!token) return null
  return { Authorization: `Bearer ${token}` }
}

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

function coverUrlForItem(item) {
  const songName = typeof item?.songName === 'string' ? item.songName : ''
  const url = coverUrlBySong(songName)
  return url || defaultSongCover
}

async function getArtistBySongName(songName) {
  const headers = getAuthHeader()
  if (!headers) return ''
  try {
    const { data } = await axios.get(`${apiBase}/api/meta/song-name`, {
      params: { songName },
      headers,
    })
    return typeof data?.artist === 'string' ? data.artist : ''
  } catch {
    return ''
  }
}

async function loadArtists(list) {
  const headers = getAuthHeader()
  if (!headers) return

  const arr = Array.isArray(list) ? list : []
  if (!arr.length) return

  const queued = []
  const seen = new Set()
  for (const x of arr) {
    const songName = typeof x?.songName === 'string' ? x.songName.trim() : ''
    if (!songName || seen.has(songName)) continue
    seen.add(songName)
    if (typeof artistBySongName.value?.[songName] === 'string') continue
    queued.push(songName)
  }
  if (!queued.length) return

  const nextMap = { ...(artistBySongName.value || {}) }
  const batchSize = 6
  for (let i = 0; i < queued.length; i += batchSize) {
    const batch = queued.slice(i, i + batchSize)
    const results = await Promise.all(batch.map(async (s) => ({ songName: s, artist: await getArtistBySongName(s) })))
    for (const r of results) nextMap[r.songName] = r.artist || ''
    artistBySongName.value = { ...nextMap }
  }
}

function artistLabelBySongName(songName) {
  const name = typeof songName === 'string' ? songName : ''
  const picked = artistBySongName.value?.[name]
  if (typeof picked === 'string' && picked.trim()) return picked
  const headers = getAuthHeader()
  if (!headers) return ''
  return '歌手加载中...'
}

async function goPlayer(songName) {
  const queue = items.value.map((x) => x?.songName).filter((x) => typeof x === 'string' && x.trim())
  const index = queue.findIndex((x) => x === songName)
  const artist = await getArtistBySongName(songName)
  window.dispatchEvent(
    new CustomEvent('player:set', {
      detail: {
        songName,
        artist,
        queue,
        index: index >= 0 ? index : 0,
        isPlaying: true,
      },
    }),
  )
  router.push(`/player?name=${encodeURIComponent(songName)}`)
}

async function loadFavorites() {
  const headers = getAuthHeader()
  if (!headers) {
    error.value = '请先登录后查看我的收藏'
    items.value = []
    artistBySongName.value = {}
    emitFavoritesChanged({ map: {} })
    return
  }

  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${apiBase}/api/favorites/username`, { headers })
    const arr = Array.isArray(data) ? data : []
    items.value = arr
    loadArtists(items.value)
    const map = {}
    for (const f of arr) {
      if (!f || !f.songName) continue
      map[f.songName] = f.id
    }
    emitFavoritesChanged({ map })
  } catch (err) {
    error.value = pickErrorMessage(err)
    items.value = []
    emitFavoritesChanged({ map: {} })
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(item) {
  const headers = getAuthHeader()
  if (!headers) {
    showToast('请先登录')
    return
  }
  const id = item?.id
  const songName = typeof item?.songName === 'string' ? item.songName : ''
  if (id == null || !songName) return

  try {
    await axios.delete(`${apiBase}/api/favorites/${id}`, { headers })
    items.value = items.value.filter((x) => x?.id !== id)
    emitFavoritesChanged({ songName, id: null })
    showToast('已取消收藏')
    const nextArtist = { ...(artistBySongName.value || {}) }
    delete nextArtist[songName]
    artistBySongName.value = nextArtist
  } catch (err) {
    showToast(pickErrorMessage(err))
  }
}

onMounted(() => {
  window.addEventListener('favorites:changed', onFavoritesChanged)
  loadFavorites()
})

onBeforeUnmount(() => {
  window.removeEventListener('favorites:changed', onFavoritesChanged)
})
</script>

<template>
  <div class="page animate-fade-in">
    <div class="card">
      <div class="head">
        <div class="title">我的收藏</div>
        <button class="btn" type="button" @click="loadFavorites">刷新</button>
      </div>

      <div class="hint" v-if="loading">加载中...</div>
      <div class="hint" v-else-if="error">{{ error }}</div>
      <div class="hint" v-else-if="items.length === 0">暂无收藏</div>
    </div>

    <div class="list card" v-if="!loading && !error && items.length">
      <div class="items">
        <div v-for="f in items" :key="f.id" class="item">
          <button class="cover-btn" type="button" @click="goPlayer(f.songName)">
            <img class="cover" :src="coverUrlForItem(f)" alt="cover" />
          </button>
          <div class="meta">
            <div class="name">{{ f.songName }}</div>
            <div class="artist" v-if="artistLabelBySongName(f.songName)">{{ artistLabelBySongName(f.songName) }}</div>
          </div>
          <button class="icon-action fav" type="button" @click="toggleFavorite(f)">
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path
                d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.62L12 2 9.19 8.62 2 9.24l5.46 4.73L5.82 21z"
                fill="currentColor"
              />
            </svg>
          </button>
          <button class="play" type="button" @click="goPlayer(f.songName)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M8 5v14l12-7L8 5Z" fill="currentColor" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.title {
  font-size: 14px;
  font-weight: 700;
}

.btn {
  height: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 12px;
  cursor: pointer;
}

.btn:hover {
  border-color: var(--accent);
}

.hint {
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
}

.list .items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
}

.cover {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  border: 1px solid var(--border);
  object-fit: cover;
  background: var(--card);
  flex: none;
}

.cover-btn {
  flex: none;
  border-radius: 14px;
  outline: none;
}

.cover-btn:hover .cover {
  border-color: var(--accent);
}

.icon-action {
  height: 36px;
  width: 36px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--accent);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.icon-action:hover {
  border-color: var(--accent);
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
}

.artist {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.play {
  height: 36px;
  width: 36px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--accent);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.play:hover {
  border-color: var(--accent);
}
</style>
