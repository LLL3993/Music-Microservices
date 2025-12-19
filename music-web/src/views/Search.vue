<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

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

watch(
  () => route.query.q,
  (q) => {
    keyword.value = typeof q === 'string' ? q : ''
  },
  { immediate: true },
)

const mockSongs = computed(() => {
  const base = [
    { name: '晴天', artist: '周杰伦' },
    { name: '七里香', artist: '周杰伦' },
    { name: '倔强', artist: '五月天' },
    { name: '温柔', artist: '五月天' },
    { name: 'K歌之王', artist: '陈奕迅' },
    { name: '演员', artist: '薛之谦' },
    { name: 'Love Story', artist: 'Taylor Swift' },
    { name: 'Hello', artist: 'Adele' },
  ]

  const key = keyword.value.trim().toLowerCase()
  if (!key) return base

  return base.filter((s) => `${s.name} ${s.artist}`.toLowerCase().includes(key))
})

function onEnter() {
  const key = keyword.value.trim()
  if (!key) return
  router.push(`/search?q=${encodeURIComponent(key)}`)
}

function selectHotTag(tag) {
  keyword.value = tag
  onEnter()
}

function goPlayer(name) {
  router.push(`/player?name=${encodeURIComponent(name)}`)
}
</script>

<template>
  <div class="page">
    <div class="card">
      <div class="title">热门搜索</div>
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
        <div v-for="(s, idx) in mockSongs" :key="`${s.name}-${idx}`" class="item">
          <img
            class="cover"
            src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
            alt="cover"
          />
          <div class="meta">
            <div class="name">{{ s.name }}</div>
            <div class="artist">{{ s.artist }}</div>
          </div>
          <button class="play" type="button" @click="goPlayer(s.name)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path
                d="M8 5v14l12-7L8 5Z"
                fill="currentColor"
              />
            </svg>
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

.title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
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
</style>
