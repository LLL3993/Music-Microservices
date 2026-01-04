<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultCover = `${baseUrl()}data/pic/playlist.png`

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
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

function requestLogin() {
  window.dispatchEvent(new CustomEvent('auth:open', { detail: { mode: 'login' } }))
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

const loading = ref(false)
const error = ref('')
const playlist = ref(null)
const details = ref([])
const detailLoading = ref(false)
const detailError = ref('')

const coverUrl = computed(() => {
  const first = details.value?.length ? details.value[0]?.songName : ''
  if (typeof first === 'string' && first.trim()) return coverUrlBySong(first)
  return defaultCover
})

function back() {
  if (window.history.length > 1) router.back()
  else router.push({ name: 'Recommended' })
}

async function goPlayer(songName) {
  if (!getAuthHeader()) {
    requestLogin()
    return
  }
  const queue = details.value.map((x) => x?.songName).filter((x) => typeof x === 'string' && x.trim())
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

async function load() {
  const playlistName = typeof route.query.playlistName === 'string' ? route.query.playlistName.trim() : ''
  const username = typeof route.query.username === 'string' ? route.query.username.trim() : ''
  if (!playlistName) {
    playlist.value = null
    details.value = []
    error.value = '缺少歌单名称'
    return
  }

  loading.value = true
  error.value = ''
  playlist.value = null
  details.value = []
  detailError.value = ''

  try {
    const authHeaders = getAuthHeader()
    let picked = null

    if (authHeaders) {
      try {
        const { data } = await axios.get(`${apiBase}/api/playlists/playlist-name`, {
          params: { playlistName },
          headers: authHeaders,
        })
        const list = Array.isArray(data) ? data : []
        picked = username ? list.find((p) => p?.username === username) : list[0]
      } catch {}
    }

    if (!picked) {
      try {
        const { data } = await axios.get(`${apiBase}/api/playlists/public`, { params: { limit: 200 } })
        const list = Array.isArray(data) ? data : []
        const sameName = list.filter((p) => p?.playlistName === playlistName)
        picked = username ? sameName.find((p) => p?.username === username) : sameName[0]
      } catch {}
    }

    if (picked) playlist.value = picked
    else playlist.value = { id: null, playlistName, username, description: '', isPublic: true }

    detailLoading.value = true
    try {
      const { data } = await axios.get(`${apiBase}/api/playlist-details`, {
        params: { playlistName, ...(username ? { username } : {}) },
      })
      const arr = Array.isArray(data) ? data : []
      arr.sort((a, b) => {
        const ai = Number(a?.id)
        const bi = Number(b?.id)
        if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
        return 0
      })
      details.value = arr
    } catch (e) {
      details.value = []
      detailError.value = pickErrorMessage(e)
    } finally {
      detailLoading.value = false
    }
  } catch (e) {
    error.value = pickErrorMessage(e)
  } finally {
    loading.value = false
  }
}

watch(
  () => [route.query.playlistName, route.query.username],
  () => {
    load()
  },
)

onMounted(() => {
  load()
  window.addEventListener('auth:changed', load)
})

onBeforeUnmount(() => {
  window.removeEventListener('auth:changed', load)
})
</script>

<template>
  <div class="page animate-fade-in">
    <div v-if="error" class="card">
      <div class="head">
        <div class="title">歌单详情</div>
        <button class="btn" type="button" @click="back">返回</button>
      </div>
      <div class="hint">{{ error }}</div>
    </div>

    <div v-else class="card">
      <div class="head">
        <div class="title">歌单详情</div>
        <button class="btn" type="button" @click="back">返回</button>
      </div>

      <div class="detail-layout">
        <div class="detail-aside">
          <div class="detail-head">
            <img class="cover" :src="coverUrl" alt="cover" />
            <div class="info">
              <div class="name">{{ playlist?.playlistName || route.query.playlistName }}</div>
              <div class="creator">创建者：{{ playlist?.username || route.query.username || '未知' }}</div>
              <div class="desc">
                <div class="desc-label">歌单简介：</div>
                <div class="desc-text">{{ playlist?.description || '无描述' }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="detail-songs">
          <div class="hint" v-if="loading || detailLoading">加载中...</div>
          <div class="hint" v-else-if="detailError">{{ detailError }}</div>
          <div class="hint" v-else-if="details.length === 0">歌单内暂无歌曲</div>

          <div v-if="!loading && !detailLoading && !detailError && details.length" class="detail-items">
            <div v-for="d in details" :key="d.id" class="detail-item">
              <div class="meta">
                <div class="name">{{ d.songName }}</div>
              </div>
              <div class="row-actions">
                <button class="icon-action" type="button" @click="goPlayer(d.songName)">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <path d="M8 5v14l12-7L8 5Z" fill="currentColor" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
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
  padding-bottom: 16px;
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
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn:hover {
  border-color: var(--accent);
}

.hint {
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
}

.detail-head {
  display: flex;
  gap: 14px;
  padding: 18px 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  margin-top: 0;
  flex-direction: column;
  align-items: stretch;
}

.detail-layout {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(320px, 460px) 1fr;
  gap: 16px;
  align-items: stretch;
  min-height: 520px;
}

.detail-aside {
  min-width: 0;
  min-height: 520px;
  max-height: calc(100vh - 320px);
  overflow: auto;
}

.detail-aside .detail-head {
  height: 100%;
}

.detail-songs {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  min-width: 0;
  min-height: 520px;
  max-height: calc(100vh - 320px);
  overflow: auto;
}

.cover {
  width: 200px;
  height: 200px;
  border-radius: 12px;
  border: 1px solid var(--border);
  object-fit: cover;
  flex: none;
  align-self: center;
}

.info {
  flex: 1;
  min-width: 0;
  width: 100%;
}

.name {
  font-size: 26px;
  font-weight: 800;
  color: var(--text);
  line-height: 1.2;
  text-align: center;
}

.creator {
  color: var(--muted);
  font-size: 15px;
  font-weight: 700;
  text-align: center;
  margin-top: 10px;
}

.desc {
  margin-top: 10px;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.55;
  background: color-mix(in srgb, var(--card) 50%, transparent);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  min-height: 190px;
}

.desc-label {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.desc-text {
  white-space: pre-wrap;
}

.detail-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
}

.meta {
  min-width: 0;
}

.meta .name {
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.icon-action {
  height: 40px;
  width: 40px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.icon-action:hover {
  border-color: var(--accent);
  color: var(--accent);
}

@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
    min-height: 0;
  }
  .detail-songs {
    min-height: 0;
    max-height: none;
  }
  .detail-head {
    flex-direction: column;
    align-items: stretch;
  }
  .cover {
    width: 160px;
    height: 160px;
  }
  .meta .name {
    white-space: normal;
  }
}
</style>
