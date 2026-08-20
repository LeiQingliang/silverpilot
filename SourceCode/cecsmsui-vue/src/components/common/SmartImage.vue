<template>
  <img
    v-if="!failed"
    v-bind="$attrs"
    :src="currentSource"
    :alt="alt"
    :loading="priority ? 'eager' : 'lazy'"
    :fetchpriority="priority ? 'high' : 'auto'"
    decoding="async"
    :style="{ objectFit: fit }"
    :class="{ 'smart-image-loading': !loaded }"
    @load="handleLoad"
    @error="handleError"
  />
  <span v-else v-bind="$attrs" class="smart-image-fallback" role="img" :aria-label="alt || fallbackLabel">
    {{ fallbackLabel }}
  </span>
</template>

<script setup>
import { ref, watch } from 'vue'
import { optimizedImageUrl } from '@/utils/media'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  fit: { type: String, default: 'cover' },
  priority: { type: Boolean, default: false },
  fallbackLabel: { type: String, default: '暂无图片' }
})
const emit = defineEmits(['load', 'error'])

const currentSource = ref('')
const attemptedOriginal = ref(false)
const failed = ref(false)
const loaded = ref(false)

const reset = () => {
  const original = props.src?.trim() || ''
  currentSource.value = optimizedImageUrl(original)
  attemptedOriginal.value = currentSource.value === original
  failed.value = !original
  loaded.value = false
}

const handleLoad = (event) => {
  loaded.value = true
  emit('load', event)
}

const handleError = (event) => {
  const original = props.src?.trim() || ''
  if (!attemptedOriginal.value && original) {
    attemptedOriginal.value = true
    currentSource.value = original
    return
  }
  failed.value = true
  emit('error', event)
}

watch(() => props.src, reset, { immediate: true })
</script>

<style scoped>
img {
  display: block;
  background: var(--sp-surface-muted);
  transition: opacity 180ms ease;
}

.smart-image-loading { opacity: .72; }

.smart-image-fallback {
  display: grid;
  place-items: center;
  color: var(--sp-text-muted);
  font-size: 13px;
  font-weight: 650;
  background: var(--sp-surface-muted);
  border: 1px solid var(--sp-border);
}

@media (prefers-reduced-motion: reduce) {
  img { transition: none; }
}
</style>
