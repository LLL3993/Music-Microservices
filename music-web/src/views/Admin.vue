<script setup>
import axios from 'axios'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const error = ref('')
const users = ref([])
const songs = ref([])
const notice = ref('')

const pageSize = 10
const userPage = ref(1)
const songPage = ref(1)

const isUserView = computed(() => route.name === 'AdminUsers')
const isSongView = computed(() => route.name === 'AdminMusic')

const userPageCount = computed(() => {
  const total = Array.isArray(users.value) ? users.value.length : 0
  return Math.max(1, Math.ceil(total / pageSize))
})

const songPageCount = computed(() => {
  const total = Array.isArray(songs.value) ? songs.value.length : 0
  return Math.max(1, Math.ceil(total / pageSize))
})

const pagedUsers = computed(() => {
  const page = Math.min(userPageCount.value, Math.max(1, userPage.value))
  const start = (page - 1) * pageSize
  return (Array.isArray(users.value) ? users.value : []).slice(start, start + pageSize)
})

const pagedSongs = computed(() => {
  const page = Math.min(songPageCount.value, Math.max(1, songPage.value))
  const start = (page - 1) * pageSize
  return (Array.isArray(songs.value) ? songs.value : []).slice(start, start + pageSize)
})

const userModalOpen = ref(false)
const userModalMode = ref('create')
const userSubmitting = ref(false)
const userFormError = ref('')
const userForm = ref({
  id: null,
  username: '',
  email: '',
  password: '',
  isAdmin: false,
})

const songModalOpen = ref(false)
const songModalMode = ref('create')
const songSubmitting = ref(false)
const songFormError = ref('')
const songForm = ref({
  id: null,
  songName: '',
  artist: '',
})

function pickErrorMessage(err) {
  const message = err?.response?.data?.message
  if (typeof message === 'string' && message.trim()) return message
  return '请求失败'
}

function setNotice(value) {
  notice.value = value
  if (!value) return
  window.setTimeout(() => {
    if (notice.value === value) notice.value = ''
  }, 1800)
}

function getAdminHeaders() {
  const token = localStorage.getItem('auth_token') || ''
  if (!token) return null
  return { Authorization: `Bearer ${token}`, 'X-User-Is-Admin': 'true' }
}

const userCount = computed(() => (Array.isArray(users.value) ? users.value.length : 0))
const songCount = computed(() => (Array.isArray(songs.value) ? songs.value.length : 0))

function goUsers() {
  router.push({ name: 'AdminUsers' })
}

function goMusic() {
  router.push({ name: 'AdminMusic' })
}

function prevUserPage() {
  userPage.value = Math.max(1, userPage.value - 1)
}

function nextUserPage() {
  userPage.value = Math.min(userPageCount.value, userPage.value + 1)
}

function prevSongPage() {
  songPage.value = Math.max(1, songPage.value - 1)
}

function nextSongPage() {
  songPage.value = Math.min(songPageCount.value, songPage.value + 1)
}

watch(
  () => userCount.value,
  () => {
    userPage.value = Math.min(userPage.value, userPageCount.value)
  },
)

watch(
  () => songCount.value,
  () => {
    songPage.value = Math.min(songPage.value, songPageCount.value)
  },
)

async function loadAll() {
  const headers = getAdminHeaders()
  if (!headers) {
    error.value = '请先登录'
    users.value = []
    songs.value = []
    return
  }

  loading.value = true
  error.value = ''
  try {
    const [userResp, songResp] = await Promise.all([
      axios.get(`${apiBase}/api/users`, { headers }),
      axios.get(`${apiBase}/api/meta`, { headers }),
    ])
    users.value = Array.isArray(userResp.data) ? userResp.data : []
    songs.value = Array.isArray(songResp.data) ? songResp.data : []
  } catch (err) {
    error.value = pickErrorMessage(err)
    users.value = []
    songs.value = []
  } finally {
    loading.value = false
  }
}

function openCreateUser() {
  userModalMode.value = 'create'
  userFormError.value = ''
  userForm.value = {
    id: null,
    username: '',
    email: '',
    password: '',
    isAdmin: false,
  }
  userModalOpen.value = true
}

function openEditUser(u) {
  userModalMode.value = 'edit'
  userFormError.value = ''
  userForm.value = {
    id: u?.id ?? null,
    username: typeof u?.username === 'string' ? u.username : '',
    email: typeof u?.email === 'string' ? u.email : '',
    password: '',
    isAdmin: Boolean(u?.isAdmin),
  }
  userModalOpen.value = true
}

function closeUserModal() {
  if (userSubmitting.value) return
  userModalOpen.value = false
  userFormError.value = ''
}

async function submitUser() {
  const headers = getAdminHeaders()
  if (!headers) {
    userFormError.value = '请先登录'
    return
  }

  const username = userForm.value.username.trim()
  const email = userForm.value.email.trim()
  const password = userForm.value.password.trim()
  const isAdmin = Boolean(userForm.value.isAdmin)

  if (!username || !email) {
    userFormError.value = '用户名和邮箱为必填'
    return
  }
  if (!password) {
    userFormError.value = '密码为必填'
    return
  }

  userSubmitting.value = true
  userFormError.value = ''
  try {
    if (userModalMode.value === 'create') {
      await axios.post(
        `${apiBase}/api/users`,
        { username, email, password, isAdmin },
        { headers },
      )
      setNotice('已新增用户')
    } else {
      const id = userForm.value.id
      await axios.put(
        `${apiBase}/api/users/${id}`,
        { username, email, password, isAdmin },
        { headers },
      )
      setNotice('已更新用户')
    }
    userModalOpen.value = false
    await loadAll()
  } catch (err) {
    userFormError.value = pickErrorMessage(err)
  } finally {
    userSubmitting.value = false
  }
}

async function deleteUser(u) {
  const id = u?.id
  if (id == null) return
  if (!window.confirm(`确认删除用户：${u?.username || id}？`)) return

  const headers = getAdminHeaders()
  if (!headers) {
    setNotice('请先登录')
    return
  }

  try {
    await axios.delete(`${apiBase}/api/users/${id}`, { headers })
    setNotice('已删除用户')
    await loadAll()
  } catch (err) {
    setNotice(pickErrorMessage(err))
  }
}

function openCreateSong() {
  songModalMode.value = 'create'
  songFormError.value = ''
  songForm.value = {
    id: null,
    songName: '',
    artist: '',
  }
  songModalOpen.value = true
}

function openEditSong(s) {
  songModalMode.value = 'edit'
  songFormError.value = ''
  songForm.value = {
    id: s?.id ?? null,
    songName: typeof s?.songName === 'string' ? s.songName : '',
    artist: typeof s?.artist === 'string' ? s.artist : '',
  }
  songModalOpen.value = true
}

function closeSongModal() {
  if (songSubmitting.value) return
  songModalOpen.value = false
  songFormError.value = ''
}

async function submitSong() {
  const headers = getAdminHeaders()
  if (!headers) {
    songFormError.value = '请先登录'
    return
  }

  const songName = songForm.value.songName.trim()
  const artist = songForm.value.artist.trim()
  if (!songName || !artist) {
    songFormError.value = '歌曲名和歌手为必填'
    return
  }

  songSubmitting.value = true
  songFormError.value = ''
  try {
    if (songModalMode.value === 'create') {
      await axios.post(`${apiBase}/api/meta`, { songName, artist }, { headers })
      setNotice('已新增音乐')
    } else {
      const id = songForm.value.id
      await axios.put(`${apiBase}/api/meta/${id}`, { songName, artist }, { headers })
      setNotice('已更新音乐')
    }
    songModalOpen.value = false
    await loadAll()
  } catch (err) {
    songFormError.value = pickErrorMessage(err)
  } finally {
    songSubmitting.value = false
  }
}

async function deleteSong(s) {
  const id = s?.id
  if (id == null) return
  if (!window.confirm(`确认删除歌曲：${s?.songName || id}？`)) return

  const headers = getAdminHeaders()
  if (!headers) {
    setNotice('请先登录')
    return
  }

  try {
    await axios.delete(`${apiBase}/api/meta/${id}`, { headers })
    setNotice('已删除音乐')
    await loadAll()
  } catch (err) {
    setNotice(pickErrorMessage(err))
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <div class="card head">
      <div class="title">{{ isUserView ? '用户管理' : isSongView ? '音乐管理' : '管理界面' }}</div>
      <div class="sub">用户：{{ userCount }}，音乐：{{ songCount }}</div>
      <div class="head-actions">
        <button class="btn tab" type="button" :class="{ active: isUserView }" @click="goUsers">用户管理</button>
        <button class="btn tab" type="button" :class="{ active: isSongView }" @click="goMusic">音乐管理</button>
        <button class="btn" type="button" @click="loadAll">刷新</button>
      </div>
      <div class="hint" v-if="notice">{{ notice }}</div>
      <div class="hint" v-if="loading">加载中...</div>
      <div class="hint" v-else-if="error">{{ error }}</div>
    </div>

    <div v-if="!loading && !error && isUserView" class="card">
      <div class="section-head">
        <div class="section-title">用户数据</div>
        <button class="btn small" type="button" @click="openCreateUser">新增</button>
      </div>
      <div class="empty" v-if="users.length === 0">暂无用户</div>
      <div class="table" v-else>
        <div class="row th">
          <div class="cell id">ID</div>
          <div class="cell">用户名</div>
          <div class="cell">邮箱</div>
          <div class="cell admin">管理员</div>
          <div class="cell actions">操作</div>
        </div>
        <div class="row" v-for="u in pagedUsers" :key="u.id">
          <div class="cell id">{{ u.id }}</div>
          <div class="cell">{{ u.username }}</div>
          <div class="cell">{{ u.email }}</div>
          <div class="cell admin">{{ u.isAdmin ? '是' : '否' }}</div>
          <div class="cell actions">
            <button class="link" type="button" @click="openEditUser(u)">编辑</button>
            <button class="link danger" type="button" @click="deleteUser(u)">删除</button>
          </div>
        </div>
      </div>
      <div v-if="users.length" class="pager">
        <button class="btn small" type="button" :disabled="userPage <= 1" @click="prevUserPage">上一页</button>
        <div class="pager-info">{{ userPage }} / {{ userPageCount }}</div>
        <button class="btn small" type="button" :disabled="userPage >= userPageCount" @click="nextUserPage">下一页</button>
      </div>
    </div>

    <div v-if="!loading && !error && isSongView" class="card">
      <div class="section-head">
        <div class="section-title">音乐数据</div>
        <button class="btn small" type="button" @click="openCreateSong">新增</button>
      </div>
      <div class="empty" v-if="songs.length === 0">暂无音乐</div>
      <div class="table" v-else>
        <div class="row th song">
          <div class="cell id">ID</div>
          <div class="cell">歌曲名</div>
          <div class="cell">歌手</div>
          <div class="cell actions">操作</div>
        </div>
        <div class="row song" v-for="s in pagedSongs" :key="s.id">
          <div class="cell id">{{ s.id }}</div>
          <div class="cell">{{ s.songName }}</div>
          <div class="cell">{{ s.artist }}</div>
          <div class="cell actions">
            <button class="link" type="button" @click="openEditSong(s)">编辑</button>
            <button class="link danger" type="button" @click="deleteSong(s)">删除</button>
          </div>
        </div>
      </div>
      <div v-if="songs.length" class="pager">
        <button class="btn small" type="button" :disabled="songPage <= 1" @click="prevSongPage">上一页</button>
        <div class="pager-info">{{ songPage }} / {{ songPageCount }}</div>
        <button class="btn small" type="button" :disabled="songPage >= songPageCount" @click="nextSongPage">下一页</button>
      </div>
    </div>

    <div v-if="userModalOpen" class="modal-mask" @click.self="closeUserModal">
      <div class="modal">
        <div class="modal-head">
          <div class="modal-title">{{ userModalMode === 'create' ? '新增用户' : '编辑用户' }}</div>
          <button class="modal-close" type="button" @click="closeUserModal">×</button>
        </div>
        <div class="form">
          <div class="field">
            <div class="label">用户名</div>
            <input v-model="userForm.username" class="field-input" placeholder="请输入用户名" />
          </div>
          <div class="field">
            <div class="label">邮箱</div>
            <input v-model="userForm.email" class="field-input" placeholder="请输入邮箱" />
          </div>
          <div class="field">
            <div class="label">密码</div>
            <input v-model="userForm.password" class="field-input" type="password" placeholder="请输入密码" />
          </div>
          <label class="check">
            <input v-model="userForm.isAdmin" type="checkbox" />
            <span>管理员</span>
          </label>
          <div class="form-error" v-if="userFormError">{{ userFormError }}</div>
          <button class="submit-btn" type="button" :disabled="userSubmitting" @click="submitUser">
            {{ userSubmitting ? '提交中...' : '提交' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="songModalOpen" class="modal-mask" @click.self="closeSongModal">
      <div class="modal">
        <div class="modal-head">
          <div class="modal-title">{{ songModalMode === 'create' ? '新增音乐' : '编辑音乐' }}</div>
          <button class="modal-close" type="button" @click="closeSongModal">×</button>
        </div>
        <div class="form">
          <div class="field">
            <div class="label">歌曲名</div>
            <input v-model="songForm.songName" class="field-input" placeholder="请输入歌曲名" />
          </div>
          <div class="field">
            <div class="label">歌手</div>
            <input v-model="songForm.artist" class="field-input" placeholder="请输入歌手" />
          </div>
          <div class="form-error" v-if="songFormError">{{ songFormError }}</div>
          <button class="submit-btn" type="button" :disabled="songSubmitting" @click="submitSong">
            {{ songSubmitting ? '提交中...' : '提交' }}
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
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 700;
}

.sub {
  grid-column: 1 / -1;
  color: var(--muted);
  font-size: 12px;
  margin-top: -4px;
}

.btn {
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  justify-self: end;
}

.hint {
  grid-column: 1 / -1;
  color: var(--muted);
  font-size: 12px;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 0;
}

.empty {
  color: var(--muted);
  font-size: 12px;
}

.table {
  display: grid;
  gap: 6px;
}

.row {
  display: grid;
  grid-template-columns: 80px 1fr 1fr 90px 120px;
  gap: 8px;
  padding: 10px 10px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--panel);
}

.row.th {
  font-size: 12px;
  font-weight: 700;
  background: color-mix(in srgb, var(--panel) 70%, var(--card));
}

.cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell.id {
  font-variant-numeric: tabular-nums;
  color: var(--muted);
}

.cell.admin {
  text-align: center;
}

.cell.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn.small {
  height: 32px;
  padding: 0 10px;
}

.link {
  border: 0;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  padding: 0;
}

.link.danger {
  color: #d44;
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

.check {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text);
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .row {
    grid-template-columns: 70px 1fr;
  }
  .cell.admin {
    text-align: left;
  }
  .cell.actions {
    justify-content: flex-start;
  }
}
</style>
