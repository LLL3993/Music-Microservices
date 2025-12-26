<template>
  <div 
    class="overlay-mask" 
    :class="{ active: visible }"
    @click="handleClick"
  >
    <div class="mask-content" @click.stop>
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  closable: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:visible', 'close'])

const handleClick = () => {
  if (props.closable) {
    emit('update:visible', false)
    emit('close')
  }
}
</script>

<style scoped>
.overlay-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(10px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.overlay-mask.active {
  opacity: 1;
  visibility: visible;
}

.mask-content {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  transform: scale(0.9);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-width: 90vw;
  max-height: 90vh;
  overflow: auto;
}

.overlay-mask.active .mask-content {
  transform: scale(1);
}

@media (max-width: 768px) {
  .mask-content {
    margin: 20px;
    max-width: calc(100vw - 40px);
    max-height: calc(100vh - 40px);
  }
}
</style>