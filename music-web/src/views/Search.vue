<script setup>
import axios from 'axios'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

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
const favoritesLoaded = ref(false)
const favoriteIdBySongName = ref({})

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
  dispatchPlayerSet(songName, artist)
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
  } catch {
    favoriteIdBySongName.value = {}
    favoritesLoaded.value = false
  }
}

function setNotice(value) {
  notice.value = value
  if (!value) return
  window.setTimeout(() => {
    if (notice.value === value) notice.value = ''
  }, 1800)
}

function isFavorited(songName) {
  return Boolean(favoriteIdBySongName.value?.[songName])
}

async function toggleFavorite(songName) {
  const authToken = getAuthToken()
  if (!authToken) {
    setNotice('请先登录')
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
      setNotice('已取消收藏')
    } else {
      const { data } = await axios.post(`${apiBase}/api/favorites`, { songName }, { headers })
      const id = data?.id
      if (id != null) {
        favoriteIdBySongName.value = { ...(favoriteIdBySongName.value || {}), [songName]: id }
      }
      setNotice('已收藏')
    }
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
            <button class="icon-action fav" type="button" @click="toggleFavorite(s.songName)">
              <svg v-if="isFavorited(s.songName)" width="18" height="18" viewBox="0 0 24 24">
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
            <button class="play" type="button" @click="playSong(s.songName, s.artist)">
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

.icon-action {
  height: 36px;
  width: 36px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
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
