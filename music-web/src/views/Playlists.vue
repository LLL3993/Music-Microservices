<script setup>
import axios from 'axios'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090'

const loading = ref(false)
const error = ref('')
const playlists = ref([])

const detailLoading = ref(false)
const detailError = ref('')
const selectedPlaylist = ref(null)
const details = ref([])

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

function goPlayer(songName) {
  router.push(`/player?name=${encodeURIComponent(songName)}`)
}

async function loadPlaylists() {
  const headers = getAuthHeader()
  if (!headers) {
    error.value = '请先登录后查看歌单'
    playlists.value = []
    return
  }

  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${apiBase}/api/playlists/username`, { headers })
    playlists.value = Array.isArray(data) ? data : []
  } catch (err) {
    error.value = pickErrorMessage(err)
    playlists.value = []
  } finally {
    loading.value = false
  }
}

async function openPlaylist(playlist) {
  selectedPlaylist.value = playlist
  details.value = []
  detailError.value = ''

  const headers = getAuthHeader()
  if (!headers) {
    detailError.value = '请先登录后查看歌单详情'
    return
  }

  detailLoading.value = true
  try {
    const { data } = await axios.get(`${apiBase}/api/playlist-details`, {
      params: { playlistName: playlist.playlistName },
      headers,
    })
    details.value = Array.isArray(data) ? data : []
  } catch (err) {
    detailError.value = pickErrorMessage(err)
    details.value = []
  } finally {
    detailLoading.value = false
  }
}

function backToList() {
  selectedPlaylist.value = null
  details.value = []
  detailError.value = ''
}

onMounted(loadPlaylists)
</script>

<template>
  <div class="page">
    <div class="card">
      <div class="head">
        <div class="title">歌单</div>
        <button class="btn" type="button" @click="loadPlaylists">刷新</button>
      </div>

      <div class="hint" v-if="loading">加载中...</div>
      <div class="hint" v-else-if="error">{{ error }}</div>
      <div class="hint" v-else-if="playlists.length === 0">暂无歌单</div>
    </div>

    <div v-if="!loading && !error && !selectedPlaylist && playlists.length" class="card">
      <div class="items">
        <button v-for="p in playlists" :key="p.id" class="playlist-item" type="button" @click="openPlaylist(p)">
          <div class="p-title">{{ p.playlistName }}</div>
          <div class="p-desc">{{ p.description || '无描述' }}</div>
        </button>
      </div>
    </div>

    <div v-if="selectedPlaylist" class="card">
      <div class="head">
        <div class="title">歌单：{{ selectedPlaylist.playlistName }}</div>
        <button class="btn" type="button" @click="backToList">返回</button>
      </div>

      <div class="hint" v-if="detailLoading">加载中...</div>
      <div class="hint" v-else-if="detailError">{{ detailError }}</div>
      <div class="hint" v-else-if="details.length === 0">歌单内暂无歌曲</div>

      <div v-if="!detailLoading && !detailError && details.length" class="detail-items">
        <div v-for="d in details" :key="d.id" class="detail-item">
          <div class="meta">
            <div class="name">{{ d.songName }}</div>
          </div>
          <button class="play" type="button" @click="goPlayer(d.songName)">播放</button>
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

.items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.playlist-item {
  text-align: left;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  cursor: pointer;
}

.playlist-item:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
}

.p-title {
  font-weight: 700;
}

.p-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}

.detail-items {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-item {
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
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--accent);
  cursor: pointer;
  white-space: nowrap;
}

.play:hover {
  border-color: var(--accent);
}
</style>

