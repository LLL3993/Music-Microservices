<script setup>
import axios from 'axios'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultPlaylistCover = 'https://dummyimage.com/320x100/999999/ff4400.png&text=PLAYLIST'

const router = useRouter()

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

const loading = ref(false)
const error = ref('')
const playlists = ref([])

async function mapWithConcurrency(items, concurrency, mapper) {
  const list = Array.isArray(items) ? items : []
  const size = Math.max(1, Math.min(Number(concurrency) || 1, 12))
  const result = new Array(list.length)
  let cursor = 0
  const workers = Array.from({ length: Math.min(size, list.length) }, async () => {
    while (cursor < list.length) {
      const idx = cursor
      cursor += 1
      result[idx] = await mapper(list[idx], idx)
    }
  })
  await Promise.all(workers)
  return result
}

async function resolveCoverUrl(playlistName, username) {
  let coverUrl = defaultPlaylistCover
  try {
    const detailResp = await axios.get(`${apiBase}/api/playlist-details`, {
      params: { playlistName, ...(username ? { username } : {}) },
    })
    const list = Array.isArray(detailResp.data) ? detailResp.data : []
    list.sort((a, b) => {
      const ai = Number(a?.id)
      const bi = Number(b?.id)
      if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
      return 0
    })
    const firstSong = list.length ? list[0]?.songName : ''
    if (typeof firstSong === 'string' && firstSong.trim()) coverUrl = coverUrlBySong(firstSong)
  } catch {}
  return coverUrl
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${apiBase}/api/playlists/public`, { params: { limit: 200 } })
    const list = Array.isArray(data) ? data : []
    const mapped = await mapWithConcurrency(list, 6, async (p) => {
      const playlistName = typeof p?.playlistName === 'string' ? p.playlistName : ''
      const username = typeof p?.username === 'string' ? p.username : ''
      if (!playlistName) return null
      const coverUrl = await resolveCoverUrl(playlistName, username)
      return { id: p?.id ?? `${username}:${playlistName}`, playlistName, username, coverUrl }
    })
    playlists.value = mapped.filter(Boolean)
  } catch (e) {
    error.value = '加载失败'
    playlists.value = []
  } finally {
    loading.value = false
  }
}

function backToDiscover() {
  router.push({ name: 'Discover' })
}

function openPublicPlaylist(p) {
  const playlistName = typeof p?.playlistName === 'string' ? p.playlistName.trim() : ''
  if (!playlistName) return
  const username = typeof p?.username === 'string' ? p.username.trim() : ''
  router.push({ name: 'PublicPlaylist', query: { playlistName, ...(username ? { username } : {}) } })
}

onMounted(() => {
  loadAll()
  window.addEventListener('auth:changed', loadAll)
})

onBeforeUnmount(() => {
  window.removeEventListener('auth:changed', loadAll)
})
</script>

<template>
  <div class="page animate-fade-in">
    <div class="head">
      <div class="title">为你推荐</div>
      <div class="head-actions">
        <button class="btn" type="button" @click="backToDiscover">返回发现音乐</button>
      </div>
    </div>

    <div class="row">
      <div v-if="loading" class="empty">加载中...</div>
      <div v-else-if="error" class="empty">{{ error }}</div>
      <div v-else-if="playlists.length === 0" class="empty">暂无公开歌单</div>
      <button v-else v-for="(p, index) in playlists" :key="p.id" class="card item hover-lift" type="button" :style="{ animationDelay: `${index * 0.06}s` }" @click="openPublicPlaylist(p)">
        <img class="item-img" :src="p.coverUrl" :alt="p.playlistName || 'playlist'" />
        <div class="item-meta">
          <div class="item-name">{{ p.playlistName }}</div>
          <div class="item-sub">创建者：{{ p.username }}</div>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 0;
  height: 100%;
  min-height: 0;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 8px;
}

.title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn {
  height: 34px;
  padding: 0 12px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 600;
  transition: var(--transition);
}

.btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  transition: var(--transition);
}

.row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  padding: 8px 8px 0;
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.empty {
  color: var(--text-muted);
  font-size: 14px;
  padding: 20px;
  text-align: center;
  font-weight: 500;
}

.item {
  padding: 12px;
  transition: var(--transition);
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-card);
  text-align: left;
}

.hover-lift:hover {
  transform: none;
}

.item:hover {
  box-shadow: var(--shadow-hover);
  border-color: var(--accent);
}

.item-img {
  width: 100%;
  height: 250px;
  border-radius: var(--radius);
  display: block;
  object-fit: cover;
  transition: var(--transition);
  filter: brightness(0.95) contrast(1.05);
}

.item:hover .item-img {
  filter: brightness(1) contrast(1.1);
}

.item-meta {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.item-name {
  font-weight: 800;
  font-size: 13px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-sub {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 980px) {
  .row {
    padding: 6px 4px 0;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
