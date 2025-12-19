<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="wrap">
    <div class="card">
      <div class="code">404</div>
      <div class="title">页面不存在</div>
      <div class="desc">你访问的页面已丢失或被移除</div>
      <button class="btn" type="button" @click="router.push({ name: 'Search' })">返回搜索</button>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  color: var(--text);
  padding: 24px;
}

.card {
  width: 520px;
  max-width: 100%;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  box-shadow: var(--shadow);
  padding: 28px;
  text-align: center;
}

.code {
  font-size: 44px;
  font-weight: 800;
  color: var(--accent);
  letter-spacing: 1px;
}

.title {
  margin-top: 10px;
  font-weight: 700;
  font-size: 18px;
}

.desc {
  margin-top: 8px;
  color: var(--muted);
}

.btn {
  margin-top: 18px;
  height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
}

.btn:hover {
  border-color: var(--accent);
}
</style>

