<script setup>
import axios from 'axios'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultPlaylistCover = 'https://dummyimage.com/200x200/999999/ff4400.png&text=PLAYLIST'

const loading = ref(false)
const error = ref('')
const playlists = ref([])

const detailLoading = ref(false)
const detailError = ref('')
const selectedPlaylist = ref(null)
const details = ref([])

const createOpen = ref(false)
const createSubmitting = ref(false)
const createError = ref('')
const createForm = ref({
  playlistName: '',
  description: '',
  isPublic: true,
})

const publicUpdating = ref(false)
const publicError = ref('')

function showToast(message) {
  window.dispatchEvent(new CustomEvent('toast:show', { detail: { message } }))
}

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

const selectedPlaylistCoverUrl = computed(() => {
  const firstSong = details.value?.[0]?.songName
  if (typeof firstSong === 'string' && firstSong.trim()) return coverUrlBySong(firstSong)
  return defaultPlaylistCover
})

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

async function goPlayer(songName) {
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
    const picked = typeof route.query.playlistName === 'string' ? route.query.playlistName.trim() : ''
    if (picked && !selectedPlaylist.value) {
      const match = playlists.value.find((p) => p?.playlistName === picked)
      if (match) openPlaylist(match)
    }
  } catch (err) {
    error.value = pickErrorMessage(err)
    playlists.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  const headers = getAuthHeader()
  if (!headers) {
    requestLogin()
    return
  }
  createOpen.value = true
  createError.value = ''
}

function closeCreate() {
  createOpen.value = false
  createSubmitting.value = false
  createError.value = ''
}

async function submitCreate() {
  const headers = getAuthHeader()
  if (!headers) {
    createError.value = '请先登录后创建歌单'
    requestLogin()
    return
  }

  const playlistName = createForm.value.playlistName.trim()
  const description = createForm.value.description.trim()
  if (!playlistName) {
    createError.value = '歌单名称为必填'
    return
  }

  createSubmitting.value = true
  createError.value = ''
  try {
    await axios.post(
      `${apiBase}/api/playlists`,
      { playlistName, description, isPublic: Boolean(createForm.value.isPublic) },
      { headers },
    )
    createForm.value.playlistName = ''
    createForm.value.description = ''
    createForm.value.isPublic = true
    closeCreate()
    await loadPlaylists()
  } catch (err) {
    createError.value = pickErrorMessage(err)
  } finally {
    createSubmitting.value = false
  }
}

async function updatePublic(nextPublic) {
  const playlist = selectedPlaylist.value
  if (!playlist) return

  const headers = getAuthHeader()
  if (!headers) {
    publicError.value = '请先登录后操作'
    requestLogin()
    return
  }

  const id = playlist?.id
  const playlistName = typeof playlist?.playlistName === 'string' ? playlist.playlistName : ''
  if (id == null || !playlistName) return

  publicUpdating.value = true
  publicError.value = ''
  try {
    const { data } = await axios.put(
      `${apiBase}/api/playlists/${id}`,
      { playlistName, description: playlist?.description ?? null, isPublic: Boolean(nextPublic) },
      { headers },
    )
    const isPublic = Boolean(data?.isPublic)
    selectedPlaylist.value = { ...(selectedPlaylist.value || {}), isPublic }
    playlists.value = playlists.value.map((p) => (p?.id === id ? { ...(p || {}), isPublic } : p))
    showToast(isPublic ? '已设为公开' : '已设为私密')
  } catch (err) {
    publicError.value = pickErrorMessage(err)
  } finally {
    publicUpdating.value = false
  }
}

async function deletePlaylist(playlist) {
  const headers = getAuthHeader()
  if (!headers) {
    error.value = '请先登录后操作'
    return
  }

  const ok = window.confirm(`确认删除歌单「${playlist.playlistName}」吗？`)
  if (!ok) return

  try {
    await axios.delete(`${apiBase}/api/playlists/${playlist.id}`, { headers })
    if (selectedPlaylist.value?.id === playlist.id) {
      backToList()
    }
    await loadPlaylists()
  } catch (err) {
    error.value = pickErrorMessage(err)
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
    const arr = Array.isArray(data) ? data : []
    arr.sort((a, b) => {
      const ai = Number(a?.id)
      const bi = Number(b?.id)
      if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
      return 0
    })
    details.value = arr
  } catch (err) {
    detailError.value = pickErrorMessage(err)
    details.value = []
  } finally {
    detailLoading.value = false
  }
}

async function deleteDetail(detail) {
  const headers = getAuthHeader()
  if (!headers) {
    detailError.value = '请先登录后操作'
    return
  }
  const ok = window.confirm(`确认从歌单移除「${detail.songName}」吗？`)
  if (!ok) return
  try {
    await axios.delete(`${apiBase}/api/playlist-details/${detail.id}`, { headers })
    details.value = details.value.filter((d) => d.id !== detail.id)
  } catch (err) {
    detailError.value = pickErrorMessage(err)
  }
}

function backToList() {
  selectedPlaylist.value = null
  details.value = []
  detailError.value = ''
}

watch(
  () => route.query.playlistName,
  (n) => {
    const picked = typeof n === 'string' ? n.trim() : ''
    if (!picked) return
    if (!playlists.value.length) return
    const match = playlists.value.find((p) => p?.playlistName === picked)
    if (match) openPlaylist(match)
  },
)

onMounted(loadPlaylists)
</script>

<template>
  <div class="page animate-fade-in">
    <div class="card">
      <div class="head">
        <div class="title">歌单</div>
        <div class="head-actions">
          <button class="btn" type="button" @click="openCreate">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            创建歌单
          </button>
          <button class="btn" type="button" @click="loadPlaylists">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path
                d="M21 12a9 9 0 1 1-2.64-6.36"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              />
              <path d="M21 3v6h-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            刷新
          </button>
        </div>
      </div>

      <div class="hint" v-if="loading">加载中...</div>
      <div class="hint" v-else-if="error">{{ error }}</div>
      <div class="hint" v-else-if="playlists.length === 0">暂无歌单</div>
    </div>

    <div v-if="!loading && !error && !selectedPlaylist && playlists.length" class="card">
      <div class="items">
        <div v-for="p in playlists" :key="p.id" class="playlist-item">
          <button class="playlist-main" type="button" @click="openPlaylist(p)">
            <div class="p-title">{{ p.playlistName }}</div>
            <div class="p-desc">{{ p.description || '无描述' }}</div>
          </button>
          <button class="icon-action danger" type="button" @click="deletePlaylist(p)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path
                d="M3 6h18M9 6V4h6v2m-7 3v11m8-11v11M5 6l1 16h12l1-16"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <div v-if="selectedPlaylist" class="card">
      <div class="head">
        <div class="title">歌单详情</div>
        <button class="btn" type="button" @click="backToList">返回</button>
      </div>

      <div class="detail-layout">
        <div class="detail-aside">
          <div class="detail-head">
            <img class="cover" :src="selectedPlaylistCoverUrl" alt="cover" />
            <div class="info">
              <div class="name">{{ selectedPlaylist.playlistName }}</div>
              <div class="creator">创建者：{{ selectedPlaylist.username }}</div>
              <div class="desc">
                <div class="desc-label">歌单简介：</div>
                <div class="desc-text">{{ selectedPlaylist.description || '无描述' }}</div>
              </div>
              <div class="public-row">
                <label class="public-check">
                  <input
                    type="checkbox"
                    class="public-checkbox"
                    :checked="Boolean(selectedPlaylist.isPublic)"
                    :disabled="publicUpdating"
                    @change="updatePublic($event.target.checked)"
                  />
                  <span class="public-text">公开</span>
                </label>
                <div class="public-hint">{{ selectedPlaylist.isPublic ? '所有人可见' : '仅自己可见' }}</div>
              </div>
              <div class="public-error" v-if="publicError">{{ publicError }}</div>
            </div>
          </div>
        </div>

        <div class="detail-songs">
          <div class="hint" v-if="detailLoading">加载中...</div>
          <div class="hint" v-else-if="detailError">{{ detailError }}</div>
          <div class="hint" v-else-if="details.length === 0">歌单内暂无歌曲</div>

          <div v-if="!detailLoading && !detailError && details.length" class="detail-items">
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
                <button class="icon-action danger" type="button" @click="deleteDetail(d)">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M3 6h18M9 6V4h6v2m-7 3v11m8-11v11M5 6l1 16h12l1-16"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-if="createOpen" class="modal-mask">
    <div class="modal">
      <div class="modal-head">
        <div class="modal-title">创建歌单</div>
        <button class="modal-close" type="button" @click="closeCreate">×</button>
      </div>

      <div class="form">
        <div class="field">
          <div class="label">歌单名称</div>
          <input v-model="createForm.playlistName" class="field-input" placeholder="请输入歌单名称" />
        </div>
        <div class="field">
          <div class="label">描述</div>
          <input v-model="createForm.description" class="field-input" placeholder="可选" />
        </div>
        <div class="field">
          <div class="label">公开</div>
          <label class="public-check form-public">
            <input v-model="createForm.isPublic" type="checkbox" class="public-checkbox" />
            <span class="public-text">允许所有人看到该歌单</span>
          </label>
        </div>

        <div class="form-error" v-if="createError">{{ createError }}</div>

        <button class="submit-btn" type="button" :disabled="createSubmitting" @click="submitCreate">
          {{ createSubmitting ? '创建中...' : '创建' }}
        </button>
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

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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

.items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.playlist-item {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.playlist-main {
  flex: 1;
  text-align: left;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  cursor: pointer;
}

.playlist-main:hover {
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

.icon-action {
  height: 40px;
  width: 40px;
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

.icon-action.danger:hover {
  border-color: #d44;
  color: #d44;
}

.detail-head {
  display: flex;
  gap: 14px;
  padding: 16px 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  margin-top: 0;
  flex-direction: column;
  align-items: center;
}

.detail-layout {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(320px, 460px) 1fr;
  gap: 12px;
  align-items: start;
}

.detail-songs {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  min-width: 0;
}

.cover {
  width: 180px;
  height: 180px;
  border-radius: 12px;
  border: 1px solid var(--border);
  object-fit: cover;
  flex: none;
}

.info {
  flex: 1;
  min-width: 0;
  width: 100%;
}

.info .name {
  font-weight: 800;
  font-size: 20px;
}

.creator {
  margin-top: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.desc {
  margin-top: 8px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.desc-label {
  color: var(--text-secondary);
  font-weight: 700;
  margin-bottom: 6px;
}

.desc-text {
  white-space: pre-wrap;
}

.public-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.public-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  cursor: pointer;
}

.public-check.form-public {
  width: fit-content;
}

.public-checkbox {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

.public-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.public-hint {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}

.public-error {
  margin-top: 8px;
  font-size: 12px;
  color: #d44;
  font-weight: 600;
}

.detail-items {
  margin-top: 0;
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

.row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  .cover {
    width: 120px;
    height: 120px;
  }
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, #000 50%, transparent);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
}

.modal {
  width: 420px;
  max-width: 100%;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 14px;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.modal-title {
  font-weight: 700;
}

.modal-close {
  height: 34px;
  width: 34px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field .label {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 12px;
  outline: none;
}

.form-error {
  color: #d44;
  font-size: 12px;
}

.submit-btn {
  height: 40px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
</style>
