<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const songName = computed(() =>
  typeof route.query.name === 'string' ? route.query.name : '未知歌曲',
)
const artistName = ref('')

const isPlaying = ref(false)
const activeLine = ref(0)
const currentTime = ref(0)
const duration = ref(0)

const lyricLoading = ref(false)
const lyricError = ref('')
const lyricItems = ref([])

const isSeeking = ref(false)
const seekingTime = ref(0)

function safeParseJson(value) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function baseUrl() {
  const b = import.meta.env.BASE_URL
  return typeof b === 'string' && b ? b : '/'
}

function musicUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/music/${encodeURIComponent(song)}.mp3`
}

function lrcUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/lrc/${encodeURIComponent(song)}.lrc`
}

function coverUrlBySong(song) {
  if (!song) return ''
  return `${baseUrl()}data/cover/${encodeURIComponent(song)}.jpg`
}

const coverUrl = computed(() => coverUrlBySong(songName.value))
const audioUrl = computed(() => musicUrlBySong(songName.value))

function clamp(num, min, max) {
  if (!Number.isFinite(num)) return min
  return Math.min(max, Math.max(min, num))
}

function formatTime(sec) {
  const s = Number.isFinite(sec) ? sec : 0
  const total = Math.max(0, Math.floor(s))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function parseLrc(text) {
  const out = []
  const lines = String(text || '').split(/\r?\n/)
  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) continue

    const tagMatches = Array.from(line.matchAll(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g))
    if (!tagMatches.length) continue

    const textPart = line.replace(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g, '').trim()
    for (const m of tagMatches) {
      const mm = Number(m[1])
      const ss = Number(m[2])
      const frac = m[3] ? Number(String(m[3]).padEnd(3, '0')) : 0
      if (!Number.isFinite(mm) || !Number.isFinite(ss) || !Number.isFinite(frac)) continue
      const t = mm * 60 + ss + frac / 1000
      out.push({ time: t, text: textPart })
    }
  }
  out.sort((a, b) => a.time - b.time)
  return out
}

async function loadLyrics(song) {
  lyricLoading.value = true
  lyricItems.value = []
  try {
    const resp = await fetch(lrcUrlBySong(song))
    if (!resp.ok) {
      lyricItems.value = [{ time: 0, text: '该歌曲暂无歌词' }]
      activeLine.value = 0
      return
    }
    const text = await resp.text()
    const parsed = parseLrc(text)
    lyricItems.value = parsed.length ? parsed : [{ time: 0, text: '该歌曲暂无歌词' }]
    activeLine.value = 0
  } catch {
    lyricItems.value = [{ time: 0, text: '该歌曲暂无歌词' }]
    activeLine.value = 0
  } finally {
    lyricLoading.value = false
  }
}

function onClose() {
  if (window.history.length > 1) router.back()
  else router.push({ name: 'Search' })
}

function onPrev() {
  window.dispatchEvent(new CustomEvent('player:prev'))
}

function onNext() {
  window.dispatchEvent(new CustomEvent('player:next'))
}

function onTogglePlay() {
  window.dispatchEvent(new CustomEvent('player:toggle'))
}

function seekTo(time, options) {
  const max = Number.isFinite(duration.value) && duration.value > 0 ? duration.value : Number.POSITIVE_INFINITY
  const t = clamp(time, 0, max)
  window.dispatchEvent(new CustomEvent('player:seek', { detail: { time: t, persist: options?.persist } }))
}

function onProgressDown() {
  isSeeking.value = true
  seekingTime.value = Number.isFinite(currentTime.value) ? currentTime.value : 0
}

function onProgressInput(e) {
  const raw = Number(e?.target?.value)
  if (!Number.isFinite(raw)) return
  const t = clamp(raw, 0, duration.value || 0)
  seekingTime.value = t
  seekTo(t, { persist: false })
}

function onProgressUp() {
  if (!isSeeking.value) return
  isSeeking.value = false
  seekTo(seekingTime.value, { persist: true })
}

function ensurePlayerHasSong(song) {
  const saved = safeParseJson(localStorage.getItem('player_state') || '')
  const savedSong = typeof saved?.songName === 'string' ? saved.songName : ''
  if (savedSong === song) return

  window.dispatchEvent(
    new CustomEvent('player:set', {
      detail: {
        songName: song,
        artist: artistName.value || '',
        queue: song ? [song] : [],
        index: 0,
        isPlaying: true,
      },
    }),
  )
}

function onPlayerState(e) {
  const detail = e?.detail || {}
  if (typeof detail?.isPlaying === 'boolean') isPlaying.value = detail.isPlaying
  if (typeof detail?.currentTime === 'number') currentTime.value = detail.currentTime
  if (typeof detail?.duration === 'number') duration.value = detail.duration
  if (typeof detail?.artist === 'string' && detail.artist.trim()) artistName.value = detail.artist
}

onMounted(() => {
  const raw = localStorage.getItem('player_state') || ''
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && typeof parsed.isPlaying === 'boolean') {
      isPlaying.value = parsed.isPlaying
    }
    if (parsed && typeof parsed === 'object' && typeof parsed.currentTime === 'number') {
      currentTime.value = parsed.currentTime
    }
    if (parsed && typeof parsed === 'object' && typeof parsed.duration === 'number') {
      duration.value = parsed.duration
    }
    if (parsed && typeof parsed === 'object' && typeof parsed.artist === 'string' && parsed.artist.trim()) {
      artistName.value = parsed.artist
    }
  } catch {}

  ensurePlayerHasSong(songName.value)
  loadLyrics(songName.value)
  window.addEventListener('player:state', onPlayerState)
})

onBeforeUnmount(() => {
  window.removeEventListener('player:state', onPlayerState)
})

watch(
  () => songName.value,
  (n) => {
    ensurePlayerHasSong(n)
    loadLyrics(n)
  },
)

watch(
  () => currentTime.value,
  (t) => {
    updateActiveLyricLine(t)
  },
)

function updateActiveLyricLine(time) {
  const arr = lyricItems.value
  if (!arr.length) return
  let idx = 0
  for (let i = 0; i < arr.length; i += 1) {
    if (arr[i].time <= time + 0.05) idx = i
    else break
  }
  activeLine.value = idx
  
  nextTick(() => {
    const container = document.querySelector('.lyrics')
    const activeElement = document.querySelector('.lyric-line.active')
    if (container && activeElement) {
      const containerRect = container.getBoundingClientRect()
      const activeRect = activeElement.getBoundingClientRect()
      const delta = activeRect.top - containerRect.top
      const target =
        container.scrollTop +
        delta -
        container.clientHeight / 2 +
        activeRect.height / 2
      const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight)
      container.scrollTo({ top: clamp(target, 0, maxScrollTop), behavior: 'smooth' })
    }
  })
}
</script>

<template>
  <div class="mask active animate-fade-in">
    <div class="panel">
      <button class="close" type="button" @click="onClose">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path
            d="M18 6 6 18M6 6l12 12"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>

      <div class="body">
        <div class="left">
          <div class="cover-wrap" :class="{ playing: isPlaying }">
            <img class="cover" :src="coverUrl" alt="cover" />
          </div>

          <div class="lyrics card">
            <div class="hint" v-if="lyricLoading">歌词加载中...</div>
            <div class="hint" v-else-if="lyricError">{{ lyricError }}</div>
            <div
              v-for="(line, idx) in lyricItems"
              :key="idx"
              class="lyric-line"
              :class="{ active: idx === activeLine }"
              @click="seekTo(line.time)"
            >
              {{ line.text }}
            </div>
          </div>
        </div>

        <div class="right">
          <div class="song">
            <div class="name">{{ songName }}</div>
            <div class="artist">{{ artistName || (songName ? '歌手加载中...' : '') }}</div>
          </div>

          <div class="controls card">
            <div class="btn-row">
              <button class="ctl icon" type="button" @click="onPrev">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M6 6v12M18 6l-8.5 6L18 18V6Z"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
              <button class="ctl primary icon" type="button" @click="onTogglePlay">
                <svg v-if="isPlaying" width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M8 5v14M16 5v14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                </svg>
                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M8 5v14l12-7L8 5Z" fill="currentColor" />
                </svg>
              </button>
              <button class="ctl icon" type="button" @click="onNext">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M18 6v12M6 6l8.5 6L6 18V6Z"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>

            <div class="progress">
              <input
                class="range"
                :class="{ seeking: isSeeking }"
                type="range"
                min="0"
                :max="duration || 0"
                step="0.01"
                :value="isSeeking ? seekingTime : currentTime"
                :disabled="!audioUrl || !duration"
                @pointerdown="onProgressDown"
                @pointerup="onProgressUp"
                @pointercancel="onProgressUp"
                @change="onProgressUp"
                @input="onProgressInput"
              />
              <div class="time">
                <span>{{ formatTime(currentTime) }}</span>
                <span>{{ formatTime(duration) }}</span>
              </div>
            </div>
          </div>

          <div class="card placeholder">
            <div class="hint">右侧区域占位（可扩展评论 / 推荐 / 队列）</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: linear-gradient(
      135deg,
      color-mix(in srgb, var(--accent) 10%, var(--bg)) 0%,
      var(--bg) 50%,
      color-mix(in srgb, var(--accent) 5%, var(--bg)) 100%
    ),
    radial-gradient(
      800px 400px at 30% 20%,
      color-mix(in srgb, var(--accent) 20%, transparent),
      transparent 60%
    );
  display: flex;
  align-items: stretch;
  justify-content: stretch;
  backdrop-filter: blur(20px);
  opacity: 0;
  visibility: hidden;
  transition: var(--transition);
}

.mask.active {
  opacity: 1;
  visibility: visible;
}

.panel {
  flex: 1;
  position: relative;
  padding: 32px;
  background: color-mix(in srgb, var(--bg) 12%, transparent);
  backdrop-filter: blur(20px);
  transform: translateY(20px);
  transition: var(--transition);
}

.mask.active .panel {
  transform: translateY(0);
}

.close {
  position: absolute;
  top: 24px;
  right: 24px;
  height: 44px;
  width: 44px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.body {
  height: 100%;
  display: grid;
  grid-template-columns: minmax(420px, 840px) 1fr;
  gap: 24px;
  padding-top: 44px;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.cover-wrap {
  width: 360px;
  height: 360px;
  border-radius: 50%;
  border: 2px solid var(--border);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--accent) 10%, var(--card)) 0%,
    var(--card) 100%
  );
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  animation: rotate 25s linear infinite;
  animation-play-state: paused;
  box-shadow: var(--shadow-card);
  transition: var(--transition);
  overflow: hidden;
}

.cover-wrap:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-hover);
}

.cover {
  width: 320px;
  height: 320px;
  border-radius: 50%;
  display: block;
  object-fit: cover;
  transition: var(--transition);
  filter: brightness(1.1) contrast(1.1);
}

.cover-wrap.playing {
  animation-play-state: running;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: 20px;
  transition: var(--transition);
}

.card:hover {
  box-shadow: var(--shadow-hover);
}

.lyrics {
  height: clamp(320px, 42vh, 460px);
  overflow-y: auto;
  scrollbar-gutter: stable;
  width: clamp(220px, 60%, 340px);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 8px;
}

.lyrics .hint {
  color: var(--text-muted);
  font-size: 13px;
  padding: 8px 4px 12px;
  text-align: center;
  font-weight: 500;
}

.lyric-line {
  padding: 12px 16px;
  border-radius: var(--radius);
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
  font-size: 14px;
  line-height: 1.5;
  border: 1px solid transparent;
}

.lyric-line:hover {
  background: var(--card-hover);
  transform: translateX(4px);
}

.lyric-line.active {
  color: var(--text);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--accent) 20%, transparent) 0%,
    color-mix(in srgb, var(--accent) 5%, transparent) 100%
  );
  border-color: var(--accent);
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.2);
  transform: translateX(8px);
}

.right {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.song .name {
  font-size: 28px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
  line-height: 1.3;
}

.song .artist {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 16px;
  font-weight: 500;
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.btn-row {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.ctl {
  height: 48px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 16px;
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.ctl.icon {
  width: 48px;
  padding: 0;
}

.ctl:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover);
}

.ctl.primary {
  background: var(--accent-gradient);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 78, 78, 0.3);
}

.ctl.primary:hover {
  background: linear-gradient(135deg, var(--accent-hover) 0%, #ff8585 100%);
  box-shadow: 0 6px 16px rgba(255, 78, 78, 0.4);
  transform: translateY(-2px);
}

.progress {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.range {
  width: 100%;
  height: 6px;
  accent-color: var(--accent);
  background: var(--border);
  border-radius: var(--radius-full);
  outline: none;
  cursor: pointer;
  transition: var(--transition);
}

.range.seeking {
  background: color-mix(in srgb, var(--accent) 35%, var(--border));
}

.range::-webkit-slider-thumb {
  appearance: none;
  height: 16px;
  width: 16px;
  border-radius: var(--radius-full);
  background: var(--accent-gradient);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(255, 78, 78, 0.4);
  transition: var(--transition);
}

.range::-moz-range-thumb {
  height: 16px;
  width: 16px;
  border-radius: var(--radius-full);
  background: var(--accent-gradient);
  cursor: pointer;
  border: none;
  box-shadow: 0 2px 8px rgba(255, 78, 78, 0.4);
  transition: var(--transition);
}

@media (hover: hover) and (pointer: fine) {
  .range::-webkit-slider-thumb:hover {
    transform: scale(1.2);
    box-shadow: 0 4px 12px rgba(255, 78, 78, 0.6);
  }

  .range::-moz-range-thumb:hover {
    transform: scale(1.2);
    box-shadow: 0 4px 12px rgba(255, 78, 78, 0.6);
  }
}

.time {
  display: flex;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 500;
}

.placeholder .hint {
  color: var(--text-muted);
  text-align: center;
  padding: 20px;
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 980px) {
  .body {
    grid-template-columns: 1fr;
  }
  .lyrics {
    max-width: 100%;
  }
}
</style>
