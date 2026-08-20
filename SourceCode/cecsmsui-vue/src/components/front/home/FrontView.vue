<template>
  <div id="front" class="public-shell">
    <FrontHeaderMenu />
    <div class="public-content" :class="{ 'immersive-home': isImmersiveHome }">
      <RouterView />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import FrontHeaderMenu from '@/components/front/home/FrontHeaderMenu.vue'

const route = useRoute()
const isImmersiveHome = computed(() => route.name === 'FrontHomeView')
</script>

<style scoped>
.public-shell { min-height: 100vh; background: var(--sp-bg); }
.public-content { min-width: 0; padding-top: var(--sp-header-height); }
.public-content.immersive-home { padding-top: 0; }
</style>
