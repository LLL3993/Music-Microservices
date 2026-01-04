<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultPlaylistCover = `${baseUrl()}data/pic/playlist.png`

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

const authedUsername = ref('')
const authedToken = ref('')
const isAuthed = computed(() => Boolean(authedToken.value))
const playlistCoverMap = ref({})

function showToast(message) {
  window.dispatchEvent(new CustomEvent('toast:show', { detail: { message } }))
}

function pickText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function detailSongNameValue(detail) {
  return (
    pickText(detail?.songName) ||
    pickText(detail?.song_name) ||
    pickText(detail?.name) ||
    pickText(detail?.title)
  )
}

function playlistNameValue(playlist) {
  return (
    pickText(playlist?.playlistName) ||
    pickText(playlist?.playListName) ||
    pickText(playlist?.playlistname) ||
    pickText(playlist?.name) ||
    pickText(playlist?.title) ||
    pickText(playlist?.playlistTitle) ||
    pickText(playlist?.playlist_title) ||
    pickText(playlist?.playlist_name)
  )
}

function playlistOwnerValue(playlist) {
  return (
    pickText(playlist?.username) ||
    pickText(playlist?.userName) ||
    pickText(playlist?.user?.username) ||
    pickText(playlist?.user?.userName) ||
    pickText(playlist?.creator) ||
    pickText(playlist?.creatorName) ||
    pickText(playlist?.owner) ||
    pickText(playlist?.user_name)
  )
}

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

function playlistKey(playlist) {
  const id = playlist?.id
  if (id != null) return String(id)
  const name = playlistNameValue(playlist)
  const user = playlistOwnerValue(playlist)
  return `${name}::${user}`
}

function playlistCoverUrl(playlist) {
  const key = playlistKey(playlist)
  const picked = playlistCoverMap.value?.[key]
  return typeof picked === 'string' && picked ? picked : defaultPlaylistCover
}

async function fetchPlaylistDetailsForCover(playlist) {
  const playlistName = playlistNameValue(playlist)
  if (!playlistName) return null

  const username = playlistOwnerValue(playlist)
  try {
    const resp = await axios.get(`${apiBase}/api/playlist-details`, {
      params: { playlistName, username },
    })
    return Array.isArray(resp.data) ? resp.data : []
  } catch (err) {
    const headers = getAuthHeader()
    if (!headers) return null
    try {
      const resp = await axios.get(`${apiBase}/api/playlist-details`, {
        params: { playlistName, username },
        headers,
      })
      return Array.isArray(resp.data) ? resp.data : []
    } catch {
      return null
    }
  }
}

async function loadPlaylistCovers(list) {
  const arr = Array.isArray(list) ? list : []
  if (!arr.length) return

  const queue = arr.filter((p) => {
    const key = playlistKey(p)
    const picked = playlistCoverMap.value?.[key]
    return !(typeof picked === 'string' && picked)
  })
  if (!queue.length) return

  const nextMap = { ...(playlistCoverMap.value || {}) }
  const batchSize = 6
  for (let i = 0; i < queue.length; i += batchSize) {
    const batch = queue.slice(i, i + batchSize)
    const results = await Promise.all(
      batch.map(async (p) => {
        const detailsArr = await fetchPlaylistDetailsForCover(p)
        const sorted = Array.isArray(detailsArr)
          ? [...detailsArr].sort((a, b) => {
              const ai = Number(a?.id)
              const bi = Number(b?.id)
              if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
              return 0
            })
          : []
        const firstSong = sorted.length ? detailSongNameValue(sorted[0]) : ''
        const cover =
          typeof firstSong === 'string' && firstSong.trim() ? coverUrlBySong(firstSong) : defaultPlaylistCover
        return { key: playlistKey(p), cover }
      }),
    )
    for (const r of results) nextMap[r.key] = r.cover
    playlistCoverMap.value = { ...nextMap }
  }
}

const selectedPlaylistCoverUrl = computed(() => {
  const firstSong = details.value?.length ? detailSongNameValue(details.value[0]) : ''
  if (typeof firstSong === 'string' && firstSong.trim()) return coverUrlBySong(firstSong)
  return defaultPlaylistCover
})

function pickErrorMessage(err) {
  const message = err?.response?.data?.message
  if (typeof message === 'string' && message.trim()) return message
  return '请求失败'
}

function getAuthHeader() {
  const t = authedToken.value || ''
  if (!t) return null
  return { Authorization: `Bearer ${t}` }
}

function syncAuthFromStorage() {
  try {
    authedToken.value = localStorage.getItem('auth_token') || ''
    const raw = localStorage.getItem('auth_user') || ''
    const parsed = raw ? JSON.parse(raw) : null
    authedUsername.value = typeof parsed?.username === 'string' ? parsed.username : ''
  } catch {
    authedToken.value = localStorage.getItem('auth_token') || ''
    authedUsername.value = ''
  }
}

const canManageSelectedPlaylist = computed(() => {
  const headers = getAuthHeader()
  if (!headers) return false
  const u = authedUsername.value
  const pUser = selectedPlaylist.value?.username
  if (!u || typeof pUser !== 'string' || !pUser) return false
  return u === pUser
})

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
  loading.value = true
  error.value = ''
  try {
    if (!headers) {
      playlists.value = []
      return
    }

    const { data } = await axios.get(`${apiBase}/api/playlists/username`, { headers })
    const raw = Array.isArray(data) ? data : []
    playlists.value = raw.map((p) => ({
      ...(p || {}),
      playlistName: playlistNameValue(p),
      username: playlistOwnerValue(p) || authedUsername.value,
    }))
    loadPlaylistCovers(playlists.value)
    const picked = typeof route.query.playlistName === 'string' ? route.query.playlistName.trim() : ''
    if (picked && !selectedPlaylist.value) {
      const owner = typeof route.query.username === 'string' ? route.query.username.trim() : ''
      const match = playlists.value.find((p) =>
        owner ? p?.playlistName === picked && p?.username === owner : p?.playlistName === picked,
      )
      if (match) await openPlaylist(match)
      else await openPlaylistByName(picked, owner)
    }
  } catch (err) {
    error.value = pickErrorMessage(err)
    playlists.value = []
  } finally {
    loading.value = false
  }
}

async function openPlaylistByName(playlistName, ownerUsername) {
  const name = typeof playlistName === 'string' ? playlistName.trim() : ''
  if (!name) return
  try {
    const { data } = await axios.get(`${apiBase}/api/playlists/playlist-name`, { params: { playlistName: name } })
    const raw = Array.isArray(data) ? data : []
    const list = raw.map((p) => ({
      ...(p || {}),
      playlistName: playlistNameValue(p),
      username: playlistOwnerValue(p),
    }))
    const owner = typeof ownerUsername === 'string' ? ownerUsername.trim() : ''
    const picked = owner ? list.find((p) => p?.username === owner) : list[0]
    if (!picked) return
    await openPlaylist(picked)
  } catch {}
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

async function updatePublic(playlist, nextPublic) {
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
    if (selectedPlaylist.value?.id === id) {
      selectedPlaylist.value = { ...(selectedPlaylist.value || {}), isPublic }
    }
    playlists.value = playlists.value.map((p) => (p?.id === id ? { ...(p || {}), isPublic } : p))
    showToast(isPublic ? '已设为公开' : '已设为私密')
  } catch (err) {
    publicError.value = pickErrorMessage(err)
  } finally {
    publicUpdating.value = false
  }
}

async function deletePlaylist(playlist) {
  openDeleteConfirm(playlist)
}

async function openPlaylist(playlist) {
  selectedPlaylist.value = {
    ...(playlist || {}),
    playlistName: playlistNameValue(playlist),
    username: playlistOwnerValue(playlist) || authedUsername.value,
  }
  details.value = []
  detailError.value = ''

  detailLoading.value = true
  try {
    let data
    try {
      const resp = await axios.get(`${apiBase}/api/playlist-details`, {
        params: { playlistName: playlistNameValue(playlist), username: playlistOwnerValue(playlist) },
      })
      data = resp.data
    } catch (err) {
      const headers = getAuthHeader()
      if (!headers) throw err
      const resp = await axios.get(`${apiBase}/api/playlist-details`, {
        params: { playlistName: playlistNameValue(playlist), username: playlistOwnerValue(playlist) },
        headers,
      })
      data = resp.data
    }

    const arr = Array.isArray(data) ? data : []
    arr.sort((a, b) => {
      const ai = Number(a?.id)
      const bi = Number(b?.id)
      if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
      return 0
    })
    details.value = arr.map((d) => ({ ...(d || {}), songName: detailSongNameValue(d) }))
  } catch (err) {
    detailError.value = pickErrorMessage(err)
    details.value = []
  } finally {
    detailLoading.value = false
  }
}

const editMetaOpen = ref(false)
const editMetaSubmitting = ref(false)
const editMetaError = ref('')
const editMetaForm = ref({
  playlistName: '',
  description: '',
  isPublic: true,
})

function openEditMeta() {
  if (!canManageSelectedPlaylist.value) return
  editMetaForm.value.playlistName = selectedPlaylist.value?.playlistName || ''
  editMetaForm.value.description = selectedPlaylist.value?.description || ''
  editMetaForm.value.isPublic = Boolean(selectedPlaylist.value?.isPublic)
  editMetaError.value = ''
  editMetaOpen.value = true
}

function closeEditMeta() {
  if (editMetaSubmitting.value) return
  editMetaOpen.value = false
  editMetaError.value = ''
}

async function submitEditMeta() {
  const headers = getAuthHeader()
  if (!headers) {
    editMetaError.value = '请先登录后操作'
    requestLogin()
    return
  }

  const id = selectedPlaylist.value?.id
  if (id == null) {
    editMetaError.value = '歌单信息缺失'
    return
  }

  const playlistName = editMetaForm.value.playlistName.trim()
  const description = editMetaForm.value.description.trim()
  const isPublic = Boolean(editMetaForm.value.isPublic)
  if (!playlistName) {
    editMetaError.value = '歌单名称为必填'
    return
  }

  editMetaSubmitting.value = true
  editMetaError.value = ''
  try {
    const { data } = await axios.put(
      `${apiBase}/api/playlists/${id}`,
      { playlistName, description, isPublic },
      { headers },
    )
    const persistedPublic = typeof data?.isPublic === 'boolean' ? data.isPublic : isPublic

    selectedPlaylist.value = { ...(selectedPlaylist.value || {}), playlistName, description, isPublic: persistedPublic }
    playlists.value = playlists.value.map((p) =>
      p?.id === id ? { ...(p || {}), playlistName, description, isPublic: persistedPublic } : p,
    )
    editMetaOpen.value = false
    showToast('已更新歌单信息')
    await openPlaylist(selectedPlaylist.value)
  } catch (err) {
    editMetaError.value = pickErrorMessage(err)
  } finally {
    editMetaSubmitting.value = false
  }
}

const deleteConfirmOpen = ref(false)
const deleteConfirmSubmitting = ref(false)
const deleteConfirmError = ref('')
const deleteConfirmPlaylist = ref(null)

function openDeleteConfirm(playlist) {
  if (!playlist) return
  deleteConfirmPlaylist.value = playlist
  deleteConfirmError.value = ''
  deleteConfirmOpen.value = true
}

function closeDeleteConfirm() {
  if (deleteConfirmSubmitting.value) return
  deleteConfirmOpen.value = false
  deleteConfirmError.value = ''
  deleteConfirmPlaylist.value = null
}

async function submitDeleteConfirm() {
  const headers = getAuthHeader()
  if (!headers) {
    deleteConfirmError.value = '请先登录后操作'
    requestLogin()
    return
  }

  const playlist = deleteConfirmPlaylist.value
  const id = playlist?.id
  if (id == null) {
    deleteConfirmError.value = '歌单信息缺失'
    return
  }

  deleteConfirmSubmitting.value = true
  deleteConfirmError.value = ''
  try {
    await axios.delete(`${apiBase}/api/playlists/${id}`, { headers })
    if (selectedPlaylist.value?.id === id) backToList()
    closeDeleteConfirm()
    await loadPlaylists()
    showToast('已删除歌单')
  } catch (err) {
    deleteConfirmError.value = pickErrorMessage(err)
  } finally {
    deleteConfirmSubmitting.value = false
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
    const owner = typeof route.query.username === 'string' ? route.query.username.trim() : ''
    const match = playlists.value.find((p) => (owner ? p?.playlistName === picked && p?.username === owner : p?.playlistName === picked))
    if (match) openPlaylist(match)
    else openPlaylistByName(picked, owner)
  },
)

onMounted(() => {
  syncAuthFromStorage()
  window.addEventListener('auth:changed', syncAuthFromStorage)
  loadPlaylists()
})

onBeforeUnmount(() => {
  window.removeEventListener('auth:changed', syncAuthFromStorage)
})
</script>

<template>
  <div class="page animate-fade-in">
    <div class="card">
      <div class="head">
        <div class="title">我的歌单</div>
        <div class="head-actions" v-if="isAuthed">
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

      <div class="hint" v-if="!isAuthed">请先登录后查看我的歌单</div>
      <div class="hint" v-else-if="loading">加载中...</div>
      <div class="hint" v-else-if="error">{{ error }}</div>
      <div class="hint" v-else-if="playlists.length === 0">暂无歌单</div>
    </div>

    <div v-if="isAuthed && !loading && !error && !selectedPlaylist && playlists.length" class="card">
      <div class="items">
        <div v-for="p in playlists" :key="p.id" class="playlist-item">
          <div
            class="playlist-main"
            role="button"
            tabindex="0"
            @click="openPlaylist(p)"
            @keydown.enter.prevent="openPlaylist(p)"
            @keydown.space.prevent="openPlaylist(p)"
          >
            <img class="p-cover" :src="playlistCoverUrl(p)" alt="cover" />
            <div class="p-info">
              <div class="p-title">{{ playlistNameValue(p) || '未命名歌单' }}</div>
              <div class="p-owner">创建者：{{ playlistOwnerValue(p) || authedUsername || '未知' }}</div>
            </div>
            <div class="p-right">
              <button
                v-if="authedUsername && playlistOwnerValue(p) === authedUsername"
                class="p-flag-toggle"
                type="button"
                :disabled="publicUpdating"
                @click.stop="updatePublic(p, !p.isPublic)"
              >
                {{ p.isPublic ? '公开' : '私密' }}
              </button>
              <div v-else class="p-flag">{{ p.isPublic ? '公开' : '私密' }}</div>
            </div>
          </div>
          <button
            v-if="getAuthHeader() && authedUsername && p.username === authedUsername"
            class="icon-action danger"
            type="button"
            @click="deletePlaylist(p)"
          >
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
        <div class="head-actions">
          <button v-if="canManageSelectedPlaylist" class="btn" type="button" @click="openEditMeta">编辑</button>
          <button class="btn" type="button" @click="backToList">返回</button>
        </div>
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
              <div class="public-row" v-if="canManageSelectedPlaylist">
                <label class="public-check">
                  <input
                    type="checkbox"
                    class="public-checkbox"
                    :checked="Boolean(selectedPlaylist.isPublic)"
                    :disabled="publicUpdating"
                    @change="updatePublic(selectedPlaylist, $event.target.checked)"
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
                <button v-if="canManageSelectedPlaylist" class="icon-action danger" type="button" @click="deleteDetail(d)">
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
        <button class="modal-close" type="button" @click="closeCreate" aria-label="关闭">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="form">
        <div class="field">
          <div class="label">歌单名称</div>
          <input v-model="createForm.playlistName" class="field-input" placeholder="请输入歌单名称" />
        </div>
        <div class="field">
          <div class="label">歌单简介</div>
          <textarea v-model="createForm.description" class="field-input textarea" placeholder="可选"></textarea>
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

  <div v-if="editMetaOpen" class="modal-mask">
    <div class="modal">
      <div class="modal-head">
        <div class="modal-title">编辑歌单</div>
        <button class="modal-close" type="button" @click="closeEditMeta" aria-label="关闭">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="form">
        <div class="field">
          <div class="label">歌单名称</div>
          <input v-model="editMetaForm.playlistName" class="field-input" placeholder="请输入歌单名称" />
        </div>
        <div class="field">
          <div class="label">歌单简介</div>
          <textarea v-model="editMetaForm.description" class="field-input textarea" placeholder="可选"></textarea>
        </div>
        <div class="field">
          <div class="label">公开</div>
          <label class="public-check form-public">
            <input v-model="editMetaForm.isPublic" type="checkbox" class="public-checkbox" />
            <span class="public-text">允许所有人看到该歌单</span>
          </label>
        </div>

        <div class="form-error" v-if="editMetaError">{{ editMetaError }}</div>

        <button class="submit-btn" type="button" :disabled="editMetaSubmitting" @click="submitEditMeta">
          {{ editMetaSubmitting ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>
  </div>

  <div v-if="deleteConfirmOpen" class="modal-mask">
    <div class="modal confirm-modal">
      <div class="modal-head">
        <div class="modal-title">删除歌单</div>
        <button class="modal-close" type="button" @click="closeDeleteConfirm" aria-label="关闭">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="confirm-message">
        确认删除歌单「{{ deleteConfirmPlaylist?.playlistName || '未命名歌单' }}」吗？
      </div>
      <div class="form-error" v-if="deleteConfirmError">{{ deleteConfirmError }}</div>

      <div class="confirm-actions">
        <button class="btn-lite" type="button" :disabled="deleteConfirmSubmitting" @click="closeDeleteConfirm">取消</button>
        <button class="btn-danger" type="button" :disabled="deleteConfirmSubmitting" @click="submitDeleteConfirm">
          {{ deleteConfirmSubmitting ? '删除中...' : '确认删除' }}
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
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.playlist-main:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
}

.p-cover {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  border: 1px solid var(--border);
  object-fit: cover;
  flex: none;
  background: var(--card);
}

.p-info {
  flex: 1;
  min-width: 0;
}

.p-title {
  font-weight: 700;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.p-owner {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}


.p-right {
  flex: none;
  margin-left: auto;
  display: flex;
  align-items: center;
}

.p-flag {
  font-weight: 700;
  color: var(--text-secondary);
}

.p-flag-toggle {
  flex: none;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--panel) 85%, transparent);
  color: var(--text-secondary);
  font-weight: 800;
  font-size: 12px;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: var(--transition);
}

.p-flag-toggle:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: var(--shadow-hover);
}

.p-flag-toggle:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.p-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

.info .name {
  font-weight: 800;
  font-size: 26px;
  line-height: 1.2;
  text-align: center;
}

.creator {
  margin-top: 10px;
  color: var(--muted);
  font-size: 15px;
  font-weight: 700;
  text-align: center;
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
  font-weight: 700;
  margin-bottom: 6px;
  font-size: 13px;
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
  justify-content: center;
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
  font-size: 0;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition: var(--transition);
}

.modal-close svg {
  display: block;
}

.modal-close:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: rotate(90deg);
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

.field-input.textarea {
  height: 76px;
  padding: 10px 12px;
  resize: none;
}

.form-error {
  color: #d44;
  font-size: 12px;
}

.confirm-modal {
  width: 440px;
}

.confirm-message {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  padding: 6px 2px 0;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
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
  font-weight: 700;
}

.btn-lite:hover:enabled {
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: var(--shadow-hover);
}

.btn-danger {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: linear-gradient(135deg, #ff4e4e 0%, #ff7b7b 100%);
  color: #fff;
  cursor: pointer;
  transition: var(--transition);
  font-weight: 800;
}

.btn-danger:hover:enabled {
  box-shadow: var(--shadow-hover);
  transform: translateY(-1px);
}

.btn-danger:disabled,
.btn-lite:disabled {
  opacity: 0.65;
  cursor: not-allowed;
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
