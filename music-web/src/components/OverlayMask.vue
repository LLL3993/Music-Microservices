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
  background: var(--overlay);
  backdrop-filter: blur(18px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  visibility: hidden;
  transition: var(--transition);
}

.overlay-mask.active {
  opacity: 1;
  visibility: visible;
}

.mask-content {
  background: var(--card-glass);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow-hover);
  backdrop-filter: blur(18px);
  transform: scale(0.9);
  transition: var(--transition);
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
