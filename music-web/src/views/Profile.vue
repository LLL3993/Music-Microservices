<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

const userAvatarUrl = computed(() => `${baseUrl()}data/pic/user.jpg`)

const token = ref('')
const authedUser = ref(null)

const loading = ref(false)
const error = ref('')
const profile = ref(null)

const editOpen = ref(false)
const editMode = ref('username')
const submitting = ref(false)
const formError = ref('')
const editForm = ref({
  username: '',
  email: '',
  originalPassword: '',
  password: '',
  confirmPassword: '',
})

function showToast(message) {
  if (!message) return
  window.dispatchEvent(new CustomEvent('toast:show', { detail: { message } }))
}

function pickErrorMessage(err) {
  const message = err?.response?.data?.message
  if (typeof message === 'string' && message.trim()) return message
  return '请求失败'
}

function back() {
  if (window.history.length > 1) router.back()
  else router.push({ name: 'Discover' })
}

function openLogin() {
  window.dispatchEvent(new CustomEvent('auth:open', { detail: { mode: 'login' } }))
}

function syncAuthFromStorage() {
  token.value = localStorage.getItem('auth_token') || ''
  try {
    authedUser.value = JSON.parse(localStorage.getItem('auth_user') || 'null')
  } catch {
    authedUser.value = null
  }
}

function resetEditForm() {
  editForm.value = { username: '', email: '', originalPassword: '', password: '', confirmPassword: '' }
  formError.value = ''
  submitting.value = false
}

function openEdit(mode) {
  editMode.value = mode
  resetEditForm()
  if (mode === 'username') editForm.value.username = profile.value?.username || authedUser.value?.username || ''
  if (mode === 'email') editForm.value.email = profile.value?.email || ''
  editOpen.value = true
}

function closeEdit() {
  editOpen.value = false
  resetEditForm()
}

async function submitEdit() {
  syncAuthFromStorage()
  if (!token.value) {
    formError.value = '请先登录'
    return
  }

  const current = profile.value
  const id = current?.id
  if (id == null) {
    formError.value = '用户信息缺失'
    return
  }

  const currentUsername = typeof current?.username === 'string' ? current.username : ''
  const currentEmail = typeof current?.email === 'string' ? current.email : ''
  const isAdmin = Boolean(current?.isAdmin)

  let nextUsername = currentUsername
  let nextEmail = currentEmail
  let nextPassword = ''
  let originalPassword = ''

  if (editMode.value === 'username') {
    nextUsername = editForm.value.username.trim()
    nextPassword = editForm.value.password.trim()
    if (!nextUsername) {
      formError.value = '用户名为必填'
      return
    }
    if (!nextPassword) {
      formError.value = '请输入密码'
      return
    }
  } else if (editMode.value === 'email') {
    nextEmail = editForm.value.email.trim()
    nextPassword = editForm.value.password.trim()
    if (!nextEmail) {
      formError.value = '邮箱为必填'
      return
    }
    if (!nextPassword) {
      formError.value = '请输入密码'
      return
    }
  } else {
    originalPassword = editForm.value.originalPassword.trim()
    nextPassword = editForm.value.password.trim()
    const confirmPassword = editForm.value.confirmPassword.trim()
    if (!originalPassword) {
      formError.value = '请输入原密码'
      return
    }
    if (!nextPassword || !confirmPassword) {
      formError.value = '密码与确认密码为必填'
      return
    }
    if (nextPassword !== confirmPassword) {
      formError.value = '两次密码不一致'
      return
    }
  }

  submitting.value = true
  formError.value = ''
  try {
    if (editMode.value === 'password') {
      try {
        await axios.post(`${apiBase}/api/auth/login`, { username: currentUsername, password: originalPassword })
      } catch {
        formError.value = '原密码错误'
        return
      }
    }

    await axios.put(
      `${apiBase}/api/users/${id}`,
      { username: nextUsername, email: nextEmail, password: nextPassword, isAdmin },
      { headers: { Authorization: `Bearer ${token.value}` } },
    )

    const nextLocalUser = {
      ...(authedUser.value || {}),
      username: nextUsername || authedUser.value?.username || '',
      email: nextEmail || authedUser.value?.email || '',
      isAdmin,
    }

    if (editMode.value === 'username' && nextUsername !== currentUsername) {
      try {
        const { data } = await axios.post(`${apiBase}/api/auth/login`, { username: nextUsername, password: nextPassword })
        const refreshedToken = typeof data?.token === 'string' ? data.token : token.value
        const refreshedUser = data?.user || nextLocalUser

        token.value = refreshedToken || ''
        localStorage.setItem('auth_token', token.value)
        localStorage.setItem('auth_user', JSON.stringify(refreshedUser))
        authedUser.value = refreshedUser
        window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: token.value, user: authedUser.value } }))
      } catch {
        localStorage.setItem('auth_user', JSON.stringify(nextLocalUser))
        authedUser.value = nextLocalUser
        window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: token.value, user: authedUser.value } }))
      }
    } else if (editMode.value === 'password') {
      try {
        const { data } = await axios.post(`${apiBase}/api/auth/login`, { username: currentUsername, password: nextPassword })
        const refreshedToken = typeof data?.token === 'string' ? data.token : token.value
        const refreshedUser = data?.user || nextLocalUser

        token.value = refreshedToken || ''
        localStorage.setItem('auth_token', token.value)
        localStorage.setItem('auth_user', JSON.stringify(refreshedUser))
        authedUser.value = refreshedUser
        window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: token.value, user: authedUser.value } }))
      } catch {
        localStorage.setItem('auth_user', JSON.stringify(nextLocalUser))
        authedUser.value = nextLocalUser
        window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: token.value, user: authedUser.value } }))
      }
    } else {
      localStorage.setItem('auth_user', JSON.stringify(nextLocalUser))
      authedUser.value = nextLocalUser
      window.dispatchEvent(new CustomEvent('auth:changed', { detail: { token: token.value, user: authedUser.value } }))
    }

    closeEdit()
    showToast('修改成功')
    await load()
  } catch (e) {
    formError.value = pickErrorMessage(e)
  } finally {
    submitting.value = false
  }
}

async function load() {
  syncAuthFromStorage()
  const username = typeof authedUser.value?.username === 'string' ? authedUser.value.username.trim() : ''
  if (!token.value || !username) {
    profile.value = null
    error.value = '请先登录后查看个人中心'
    return
  }

  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${apiBase}/api/users/username`, {
      params: { username },
      headers: { Authorization: `Bearer ${token.value}` },
    })
    profile.value = data || null
  } catch (e) {
    profile.value = null
    error.value = pickErrorMessage(e)
  } finally {
    loading.value = false
  }
}

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
    <div class="head">
      <div class="title">个人中心</div>
      <div class="head-actions">
        <button class="btn" type="button" @click="back">返回</button>
      </div>
    </div>

    <div class="card">
      <div v-if="loading" class="hint">加载中...</div>
      <div v-else-if="error" class="hint">
        <div>{{ error }}</div>
        <button v-if="!token" class="btn" type="button" @click="openLogin">去登录</button>
      </div>
      <div v-else class="profile">
        <div class="profile-head">
          <img class="avatar" :src="userAvatarUrl" alt="avatar" />
          <div class="meta">
            <div class="name">{{ profile?.username || authedUser?.username }}</div>
            <div class="sub">{{ profile?.email || '-' }}</div>
          </div>
        </div>

        <div class="rows">
          <div class="row">
            <div class="label">用户名</div>
            <div class="right">
              <div class="value">{{ profile?.username ?? '-' }}</div>
              <button class="link-btn" type="button" @click="openEdit('username')">修改</button>
            </div>
          </div>
          <div class="row">
            <div class="label">邮箱</div>
            <div class="right">
              <div class="value">{{ profile?.email ?? '-' }}</div>
              <button class="link-btn" type="button" @click="openEdit('email')">修改</button>
            </div>
          </div>
          <div class="row">
            <div class="label">密码</div>
            <div class="right">
              <div class="value">******</div>
              <button class="link-btn" type="button" @click="openEdit('password')">修改</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-if="editOpen" class="modal-mask">
    <div class="modal">
      <div class="modal-head">
        <div class="modal-title">
          {{ editMode === 'username' ? '修改用户名' : editMode === 'email' ? '修改邮箱' : '修改密码' }}
        </div>
        <button class="modal-close" type="button" @click="closeEdit" aria-label="关闭">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="form">
        <div v-if="editMode === 'username'" class="field">
          <div class="label">新用户名</div>
          <input v-model="editForm.username" class="field-input" placeholder="请输入新用户名" />
        </div>
        <div v-if="editMode === 'email'" class="field">
          <div class="label">新邮箱</div>
          <input v-model="editForm.email" class="field-input" placeholder="请输入新邮箱" />
        </div>

        <div v-if="editMode !== 'password'" class="field">
          <div class="label">密码</div>
          <input v-model="editForm.password" class="field-input" type="password" placeholder="请输入密码" />
        </div>

        <div v-if="editMode === 'password'" class="field">
          <div class="label">原密码</div>
          <input v-model="editForm.originalPassword" class="field-input" type="password" placeholder="请输入原密码" />
        </div>
        <div v-if="editMode === 'password'" class="field">
          <div class="label">新密码</div>
          <input v-model="editForm.password" class="field-input" type="password" placeholder="请输入新密码" />
        </div>
        <div v-if="editMode === 'password'" class="field">
          <div class="label">确认新密码</div>
          <input v-model="editForm.confirmPassword" class="field-input" type="password" placeholder="请再次输入新密码" />
        </div>

        <div class="form-error" v-if="formError">{{ formError }}</div>

        <button class="submit-btn" type="button" :disabled="submitting" @click="submitEdit">
          {{ submitting ? '提交中...' : '保存' }}
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
  font-weight: 700;
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
  padding: 14px;
}

.hint {
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 600;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.profile {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  height: 80px;
  width: 80px;
  border-radius: var(--radius-full);
  border: 2px solid var(--border);
  object-fit: cover;
  flex: none;
}

.meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  font-size: 24px;
  font-weight: 800;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sub {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
}

.label {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
  flex: none;
}

.value {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.link-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-secondary);
  cursor: pointer;
  flex: none;
  transition: var(--transition);
  font-weight: 700;
}

.link-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(20px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal {
  width: 420px;
  max-width: 100%;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
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
  font-weight: 800;
  color: var(--text);
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
}

.modal-close svg {
  display: block;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field .label {
  font-size: 12px;
  color: var(--text-secondary);
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
  font-weight: 700;
}

.submit-btn {
  height: 40px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
</style>
