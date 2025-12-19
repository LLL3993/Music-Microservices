<script setup>
import axios from 'axios'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8090'

const hotTags = [
  '周杰伦',
  '五月天',
  'Taylor Swift',
  '林俊杰',
  '陈奕迅',
  'Adele',
  '说唱',
  '电子',
]

const keyword = ref('')
const loading = ref(false)
const error = ref('')
const results = ref([])
const notice = ref('')
const playlists = ref([])
const playlistsLoaded = ref(false)
const selectedPlaylistByKey = ref({})

watch(
  () => route.query.q,
  async (q) => {
    keyword.value = typeof q === 'string' ? q : ''
    await doSearch()
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

function onEnter() {
  const key = keyword.value.trim()
  if (!key) return
  router.push(`/search?q=${encodeURIComponent(key)}`)
}

function selectHotTag(tag) {
  keyword.value = tag
  onEnter()
}

function goPlayer(songName) {
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

function setNotice(value) {
  notice.value = value
  if (!value) return
  window.setTimeout(() => {
    if (notice.value === value) notice.value = ''
  }, 1800)
}

async function favoriteSong(songName) {
  const authToken = getAuthToken()
  if (!authToken) {
    setNotice('请先登录')
    return
  }
  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    await axios.post(
      `${apiBase}/api/favorites`,
      { songName },
      { headers },
    )
    setNotice('已收藏')
  } catch (err) {
    setNotice(pickErrorMessage(err))
  }
}

async function addToPlaylist(songName, key) {
  const authToken = getAuthToken()
  if (!authToken) {
    setNotice('请先登录')
    return
  }

  const playlistName = selectedPlaylistByKey.value[key] || ''
  if (!playlistName) {
    setNotice('请选择歌单')
    return
  }

  try {
    const headers = { Authorization: `Bearer ${authToken}` }
    await axios.post(
      `${apiBase}/api/playlist-details`,
      { playlistName, songName },
      { headers },
    )
    setNotice('已加入歌单')
  } catch (err) {
    setNotice(pickErrorMessage(err))
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
  }
}
</script>

<template>
  <div class="page">
    <div class="card">
      <div class="title">热门搜索</div>
      <div class="notice" v-if="notice">{{ notice }}</div>
      <div class="tags">
        <button
          v-for="t in hotTags"
          :key="t"
          class="tag"
          type="button"
          @click="selectHotTag(t)"
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

        <div v-for="(s, idx) in results" :key="s.id ?? `${s.songName}-${idx}`" class="item">
          <img
            class="cover"
            src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
            alt="cover"
          />
          <div class="meta">
            <div class="name">{{ s.songName }}</div>
            <div class="artist">{{ s.artist }}</div>
          </div>
          <div class="actions">
            <button class="action-btn" type="button" @click="favoriteSong(s.songName)">收藏</button>
            <div class="add-row">
              <select
                class="select"
                :value="selectedPlaylistByKey[s.id ?? `${s.songName}-${idx}`] || ''"
                @change="selectedPlaylistByKey[s.id ?? `${s.songName}-${idx}`] = $event.target.value"
              >
                <option value="">选择歌单</option>
                <option v-for="p in playlists" :key="p.id" :value="p.playlistName">
                  {{ p.playlistName }}
                </option>
              </select>
              <button class="action-btn" type="button" @click="addToPlaylist(s.songName, s.id ?? `${s.songName}-${idx}`)">
                加入
              </button>
            </div>
            <button class="play" type="button" @click="goPlayer(s.songName)">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M8 5v14l12-7L8 5Z" fill="currentColor" />
              </svg>
            </button>
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
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 16px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.notice {
  font-size: 12px;
  color: var(--muted);
  margin: -4px 0 12px;
}

.sub {
  color: var(--muted);
  font-weight: 400;
  margin-left: 8px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.tag {
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text);
  height: 32px;
  padding: 0 12px;
  cursor: pointer;
}

.tag:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  color: var(--accent);
}

.search-row {
  display: flex;
  gap: 10px;
}

.input {
  width: 100%;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 12px;
  outline: none;
}

.input::placeholder {
  color: var(--muted);
}

.items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty {
  color: var(--muted);
  font-size: 12px;
  padding: 10px 2px;
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
  width: 54px;
  height: 54px;
  border-radius: 12px;
  border: 1px solid var(--border);
  object-fit: cover;
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
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}

.play {
  height: 36px;
  width: 36px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--accent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.play:hover {
  border-color: var(--accent);
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  height: 32px;
  padding: 0 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  cursor: pointer;
  white-space: nowrap;
}

.action-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.add-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.select {
  height: 32px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  padding: 0 10px;
  outline: none;
  max-width: 160px;
}
</style>
