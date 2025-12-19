<script setup>
const playlistCovers = [1, 2, 3]
</script>

<template>
  <div class="page">
    <div class="card banner">
      <img
        class="banner-img"
        src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
        alt="banner"
      />
    </div>

    <div class="section-title">歌单</div>

    <div class="row">
      <div v-for="i in playlistCovers" :key="i" class="card item">
        <img
          class="item-img"
          src="https://dummyimage.com/320x100/999999/ff4400.png&text=MUSIC"
          alt="playlist"
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
}

.banner-img {
  width: 100%;
  height: 160px;
  border-radius: 12px;
  display: block;
  object-fit: cover;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  margin-top: 2px;
}

.row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.item {
  padding: 12px;
}

.item-img {
  width: 100%;
  height: 120px;
  border-radius: 12px;
  display: block;
  object-fit: cover;
}

@media (max-width: 980px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>

