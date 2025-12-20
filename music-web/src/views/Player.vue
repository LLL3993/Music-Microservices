<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
  lyricError.value = ''
  lyricItems.value = []
  try {
    const resp = await fetch(lrcUrlBySong(song))
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const text = await resp.text()
    const parsed = parseLrc(text)
    lyricItems.value = parsed.length ? parsed : [{ time: 0, text: '暂无歌词' }]
    activeLine.value = 0
  } catch {
    lyricError.value = '歌词加载失败'
    lyricItems.value = [{ time: 0, text: '暂无歌词' }]
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
  const t = clamp(time, 0, duration.value || 0)
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
    const arr = lyricItems.value
    if (!arr.length) return
    let idx = 0
    for (let i = 0; i < arr.length; i += 1) {
      if (arr[i].time <= t + 0.05) idx = i
      else break
    }
    activeLine.value = idx
  },
)
</script>

<template>
  <div class="mask">
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
  background: radial-gradient(
      1200px 600px at 30% 20%,
      color-mix(in srgb, var(--accent) 18%, transparent),
      transparent 70%
    ),
    var(--bg);
  display: flex;
  align-items: stretch;
  justify-content: stretch;
}

.panel {
  flex: 1;
  position: relative;
  padding: 22px;
}

.close {
  position: absolute;
  top: 18px;
  right: 18px;
  height: 40px;
  width: 40px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  cursor: pointer;
}

.body {
  height: 100%;
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 18px;
  padding-top: 34px;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.cover-wrap {
  width: 320px;
  height: 320px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--panel) 70%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.cover {
  width: 280px;
  height: 280px;
  border-radius: 999px;
  border: 1px solid var(--border);
  display: block;
  object-fit: cover;
}

.cover-wrap.playing {
  animation: rotate 20s linear infinite;
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
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 16px;
}

.lyrics {
  flex: 1;
  overflow: auto;
  max-width: 420px;
  margin: 0 auto;
}

.lyrics .hint {
  color: var(--muted);
  font-size: 12px;
  padding: 6px 4px 10px;
}

.lyric-line {
  padding: 8px 10px;
  border-radius: 12px;
  color: var(--muted);
  cursor: pointer;
}

.lyric-line.active {
  color: var(--text);
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent) 40%, var(--border));
}

.right {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.song .name {
  font-size: 22px;
  font-weight: 700;
}

.song .artist {
  margin-top: 6px;
  color: var(--muted);
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.btn-row {
  display: flex;
  gap: 10px;
}

.ctl {
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  padding: 0 14px;
  cursor: pointer;
}

.ctl.icon {
  width: 44px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ctl.primary {
  background: var(--accent);
  border-color: transparent;
  color: #fff;
}

.progress {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.range {
  width: 100%;
  accent-color: var(--accent);
}

.time {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 12px;
}

.placeholder .hint {
  color: var(--muted);
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
