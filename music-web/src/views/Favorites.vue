<script setup>
import axios from 'axios'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

const loading = ref(false)
const error = ref('')
const items = ref([])

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
    return
  }
  if (items.value.some((x) => x?.songName === songName)) return
  items.value = [{ id, songName }, ...items.value]
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
    emitFavoritesChanged({ map: {} })
    return
  }

  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${apiBase}/api/favorites/username`, { headers })
    const arr = Array.isArray(data) ? data : []
    items.value = arr
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
          <div class="meta">
            <div class="name">{{ f.songName }}</div>
          </div>
          <button class="icon-action fav" type="button" @click="toggleFavorite(f)">
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path
                d="M12 21s-7-4.4-9.5-8.3C.5 9.4 2.2 6 6 6c2.1 0 3.4 1.2 4 2 0.6-0.8 1.9-2 4-2 3.8 0 5.5 3.4 3.5 6.7C19 16.6 12 21 12 21Z"
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
