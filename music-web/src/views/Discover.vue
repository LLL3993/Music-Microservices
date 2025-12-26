<script setup>
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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

function getAuthHeader() {
  const token = localStorage.getItem('auth_token') || ''
  if (!token) return null
  return { Authorization: `Bearer ${token}` }
}

function openPlaylist(playlistName) {
  if (typeof playlistName !== 'string' || !playlistName.trim()) return
  router.push({ name: 'Playlists', query: { playlistName } })
}

const recommendedPlaylists = ref([])
const recommendedLoading = ref(false)

const banners = [1, 2, 3, 4, 5].map((i) => `${baseUrl()}data/pic/show_${i}.jpg`)
const activeBanner = ref(0)
const isHovering = ref(false)
const bannerUrl = computed(() => banners[activeBanner.value] || banners[0] || '')
const prevBannerUrl = computed(() => {
  if (!banners.length) return ''
  const idx = (activeBanner.value - 1 + banners.length) % banners.length
  return banners[idx] || ''
})
const nextBannerUrl = computed(() => {
  if (!banners.length) return ''
  const idx = (activeBanner.value + 1) % banners.length
  return banners[idx] || ''
})

const isAnimating = ref(false)
const animDirection = ref(1)
const animFromUrl = ref('')
const animToUrl = ref('')

function switchBanner(direction) {
  if (!banners.length) return
  if (banners.length === 1) return
  if (isAnimating.value) return
  const fromIndex = activeBanner.value
  const toIndex =
    direction === 1
      ? (fromIndex + 1) % banners.length
      : (fromIndex - 1 + banners.length) % banners.length

  animDirection.value = direction
  animFromUrl.value = banners[fromIndex] || ''
  animToUrl.value = banners[toIndex] || ''
  activeBanner.value = toIndex

  isAnimating.value = true
  window.setTimeout(() => {
    isAnimating.value = false
  }, 460)
}

function nextBanner() {
  switchBanner(1)
}

function prevBanner() {
  switchBanner(-1)
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
    const { data } = await axios.get(`${apiBase}/api/playlists/public`, {
      params: { limit: 4 },
      headers,
    })
    const list = Array.isArray(data) ? data : []
    const picked = list.slice(0, 4)
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
        details.sort((a, b) => {
          const ai = Number(a?.id)
          const bi = Number(b?.id)
          if (Number.isFinite(ai) && Number.isFinite(bi) && ai !== bi) return ai - bi
          return 0
        })
        const firstSong = details.length ? details[0]?.songName : ''
        if (typeof firstSong === 'string' && firstSong.trim()) coverUrl = coverUrlBySong(firstSong)
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
    if (!isHovering.value && !isAnimating.value) nextBanner()
  }, 5000)
  loadRecommendedPlaylists()
})

onBeforeUnmount(() => {
  if (bannerTimer) clearInterval(bannerTimer)
  bannerTimer = null
})
</script>

<template>
  <div class="page animate-fade-in">
    <div
      class="card banner hover-scale"
      @mouseenter="isHovering = true"
      @mouseleave="isHovering = false"
    >
      <div class="banner-stage">
        <img
          v-if="banners.length > 1"
          class="banner-img banner-prev"
          :src="prevBannerUrl"
          alt="prev banner"
        />

        <img
          v-if="isAnimating"
          class="banner-img banner-current banner-leaving"
          :class="animDirection === 1 ? 'dir-next' : 'dir-prev'"
          :src="animFromUrl"
          alt="banner"
        />
        <img
          class="banner-img banner-current"
          :class="[
            isAnimating ? 'banner-entering' : '',
            isAnimating ? (animDirection === 1 ? 'dir-next' : 'dir-prev') : '',
          ]"
          :src="isAnimating ? animToUrl : bannerUrl"
          alt="banner"
        />

        <img
          v-if="banners.length > 1"
          class="banner-img banner-next"
          :src="nextBannerUrl"
          alt="next banner"
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
    </div>

    <div class="section-title">推荐歌单</div>

    <div class="row">
      <div v-if="recommendedLoading" class="empty">加载中...</div>
      <div v-else-if="recommendedPlaylists.length === 0" class="empty">暂无推荐歌单</div>
      <div
        v-for="(p, index) in recommendedPlaylists"
        :key="p.id"
        class="card item hover-lift"
        :style="{ animationDelay: `${index * 0.1}s` }"
        @click="openPlaylist(p.playlistName)"
      >
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
  gap: 24px;
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  transition: var(--transition);
}

.card:hover {
  box-shadow: var(--shadow-hover);
}

.banner {
  padding: 16px;
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.banner-stage {
  --img-w: clamp(520px, 72%, 760px);
  --side-offset: 260px;
  --side-scale: 0.88;
  --side-opacity: 0.3;
  position: relative;
  height: 320px;
  width: min(980px, 100%);
  margin: 0 auto;
  border-radius: var(--radius);
  overflow: hidden;
}

.banner-img {
  position: absolute;
  top: 0;
  left: 50%;
  width: var(--img-w);
  height: 100%;
  border-radius: var(--radius);
  object-fit: cover;
  transition: transform 0.45s ease, opacity 0.45s ease, filter 0.45s ease;
  filter: brightness(0.95) contrast(1.05);
}

.banner-current {
  z-index: 3;
  transform: translateX(-50%);
}

.banner-prev,
.banner-next {
  opacity: var(--side-opacity);
  z-index: 1;
}

.banner-prev {
  transform: translateX(calc(-50% - var(--side-offset))) scale(var(--side-scale));
}

.banner-next {
  transform: translateX(calc(-50% + var(--side-offset))) scale(var(--side-scale));
}

.banner:hover .banner-current {
  filter: brightness(1) contrast(1.1);
}

@keyframes banner-in-next {
  from {
    opacity: 0.2;
    transform: translateX(calc(-50% + 110%)) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) scale(1);
  }
}

@keyframes banner-out-next {
  from {
    opacity: 1;
    transform: translateX(-50%) scale(1);
  }
  to {
    opacity: 0;
    transform: translateX(calc(-50% - 110%)) scale(1.02);
  }
}

@keyframes banner-in-prev {
  from {
    opacity: 0.2;
    transform: translateX(calc(-50% - 110%)) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) scale(1);
  }
}

@keyframes banner-out-prev {
  from {
    opacity: 1;
    transform: translateX(-50%) scale(1);
  }
  to {
    opacity: 0;
    transform: translateX(calc(-50% + 110%)) scale(1.02);
  }
}

.banner-current {
  z-index: 3;
}

.banner-leaving {
  z-index: 3;
  pointer-events: none;
}

.banner-entering {
  z-index: 4;
  pointer-events: none;
}

.banner-entering.dir-next {
  animation: banner-in-next 0.45s ease both;
}

.banner-leaving.dir-next {
  animation: banner-out-next 0.45s ease both;
}

.banner-entering.dir-prev {
  animation: banner-in-prev 0.45s ease both;
}

.banner-leaving.dir-prev {
  animation: banner-out-prev 0.45s ease both;
}

.banner-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  height: 44px;
  width: 44px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: var(--transition);
  opacity: 0;
  backdrop-filter: blur(10px);
  z-index: 20;
}

.banner:hover .banner-nav {
  opacity: 1;
}

.banner-nav:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-50%) scale(1.1);
  box-shadow: var(--shadow-hover);
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
  font-size: 24px;
  font-weight: 600;
  margin-top: 8px;
  text-align: center;
  color: var(--text);
  margin-bottom: 16px;
}

.row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  padding: 4px 8px 16px;
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
}

.item:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-hover);
  border-color: var(--accent);
}

.item-img {
  width: 100%;
  height: 150px;
  border-radius: var(--radius);
  display: block;
  object-fit: cover;
  transition: var(--transition);
  filter: brightness(0.95) contrast(1.05);
}

.item:hover .item-img {
  transform: scale(1.05);
  filter: brightness(1) contrast(1.1);
}

@media (max-width: 980px) {
  .row {
    padding: 2px 4px 10px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
