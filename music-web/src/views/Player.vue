<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const songName = computed(() =>
  typeof route.query.name === 'string' ? route.query.name : '未知歌曲',
)
const artistName = ref('未知歌手')

const isPlaying = ref(true)
const activeLine = ref(0)

const lyricLines = [
  '第一行歌词（假数据）',
  '第二行歌词（假数据）',
  '第三行歌词（假数据）',
  '第四行歌词（假数据）',
  '第五行歌词（假数据）',
]

function onClose() {
  if (window.history.length > 1) router.back()
  else router.push({ name: 'Search' })
}

function onPrev() {}
function onNext() {}
function onTogglePlay() {
  isPlaying.value = !isPlaying.value
}

function setActiveLine(index) {
  activeLine.value = index
}
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
            <img
              class="cover"
              src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
              alt="cover"
            />
          </div>

          <div class="lyrics card">
            <div
              v-for="(line, idx) in lyricLines"
              :key="idx"
              class="lyric-line"
              :class="{ active: idx === activeLine }"
              @click="setActiveLine(idx)"
            >
              {{ line }}
            </div>
          </div>
        </div>

        <div class="right">
          <div class="song">
            <div class="name">{{ songName }}</div>
            <div class="artist">{{ artistName }}</div>
          </div>

          <div class="controls card">
            <div class="btn-row">
              <button class="ctl" type="button" @click="onPrev">上一首</button>
              <button class="ctl primary" type="button" @click="onTogglePlay">
                {{ isPlaying ? '暂停' : '播放' }}
              </button>
              <button class="ctl" type="button" @click="onNext">下一首</button>
            </div>

            <div class="progress">
              <input class="range" type="range" min="0" max="100" :value="0" />
              <div class="time">
                <span>00:00</span>
                <span>00:00</span>
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
