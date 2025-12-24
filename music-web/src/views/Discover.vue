<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const apiBase = import.meta.env.VITE_API_BASE_URL || ''
const defaultPlaylistCover = 'https://dummyimage.com/320x100/999999/ff4400.png&text=PLAYLIST'

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

function getAuthHeader() {
  const token = localStorage.getItem('auth_token') || ''
  if (!token) return null
  return { Authorization: `Bearer ${token}` }
}

const recommendedPlaylists = ref([])
const recommendedLoading = ref(false)

const banners = [1, 2, 3, 4, 5].map((i) => `${baseUrl()}data/pic/show_${i}.jpg`)
const activeBanner = ref(0)
const isHovering = ref(false)
const bannerUrl = computed(() => banners[activeBanner.value] || banners[0] || '')

function nextBanner() {
  activeBanner.value = (activeBanner.value + 1) % banners.length
}

function prevBanner() {
  activeBanner.value = (activeBanner.value - 1 + banners.length) % banners.length
}

let bannerTimer = null

async function loadRecommendedPlaylists() {
  const headers = getAuthHeader()
  if (!headers) {
    recommendedPlaylists.value = []
    return
  }

  recommendedLoading.value = true
  try {
    const { data } = await axios.get(`${apiBase}/api/playlists/username`, { headers })
    const list = Array.isArray(data) ? data : []
    const picked = list.slice(0, 3)
    const next = []
    for (const p of picked) {
      const playlistName = typeof p?.playlistName === 'string' ? p.playlistName : ''
      if (!playlistName) continue
      let coverUrl = defaultPlaylistCover
      try {
        const detailResp = await axios.get(`${apiBase}/api/playlist-details`, {
          params: { playlistName },
          headers,
        })
        const details = Array.isArray(detailResp.data) ? detailResp.data : []
        const lastSong = details.length ? details[details.length - 1]?.songName : ''
        if (typeof lastSong === 'string' && lastSong.trim()) coverUrl = coverUrlBySong(lastSong)
      } catch {}
      next.push({ id: p?.id ?? playlistName, playlistName, coverUrl })
    }
    recommendedPlaylists.value = next
  } finally {
    recommendedLoading.value = false
  }
}

onMounted(() => {
  bannerTimer = setInterval(() => {
    if (!isHovering.value) nextBanner()
  }, 5000)
  loadRecommendedPlaylists()
})

onBeforeUnmount(() => {
  if (bannerTimer) clearInterval(bannerTimer)
  bannerTimer = null
})
</script>

<template>
  <div class="page">
    <div
      class="card banner"
      @mouseenter="isHovering = true"
      @mouseleave="isHovering = false"
    >
      <img
        class="banner-img"
        :src="bannerUrl"
        alt="banner"
      />
      <button v-if="isHovering" class="banner-nav left" type="button" @click="prevBanner">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path
            d="M15 18 9 12l6-6"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <button v-if="isHovering" class="banner-nav right" type="button" @click="nextBanner">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path
            d="m9 18 6-6-6-6"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </div>

    <div class="section-title">推荐歌单</div>

    <div class="row">
      <div v-if="recommendedLoading" class="empty">加载中...</div>
      <div v-else-if="recommendedPlaylists.length === 0" class="empty">暂无推荐歌单</div>
      <div v-for="p in recommendedPlaylists" :key="p.id" class="card item">
        <img
          class="item-img"
          :src="p.coverUrl"
          :alt="p.playlistName || 'playlist'"
        />
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
}

.banner {
  padding: 12px;
  position: relative;
}

.banner-img {
  width: 100%;
  height: 320px;
  border-radius: 12px;
  display: block;
  object-fit: cover;
}

.banner-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  height: 36px;
  width: 36px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--border) 40%, transparent);
  background: color-mix(in srgb, var(--panel) 75%, transparent);
  backdrop-filter: blur(6px);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.banner-nav:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.banner-nav.left {
  left: 18px;
}

.banner-nav.right {
  right: 18px;
}

.section-title {
  font-size: 22px;
  font-weight: 800;
  margin-top: 8px;
  text-align: center;
}

.row {
  display: flex;
  gap: 16px;
  padding: 2px 8px 10px;
  overflow-x: auto;
}

.empty {
  color: var(--muted);
  font-size: 12px;
  padding: 12px;
}

.item {
  padding: 12px;
  flex: 0 0 380px;
}

.item-img {
  width: 100%;
  height: 210px;
  border-radius: 12px;
  display: block;
  object-fit: cover;
}

@media (max-width: 980px) {
  .row {
    padding: 2px 4px 10px;
  }

  .item {
    flex-basis: 78vw;
  }
}
</style>
