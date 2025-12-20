<script setup>
import axios from 'axios'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090'

const loading = ref(false)
const error = ref('')
const items = ref([])

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
    return
  }

  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${apiBase}/api/favorites/username`, { headers })
    items.value = Array.isArray(data) ? data : []
  } catch (err) {
    error.value = pickErrorMessage(err)
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadFavorites)
</script>

<template>
  <div class="page">
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
