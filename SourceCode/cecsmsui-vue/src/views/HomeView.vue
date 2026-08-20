<template>
  <div id="home" class="admin-shell">
    <HomeHeader @toggle-navigation="mobileMenuOpen = !mobileMenuOpen" />
    <div class="admin-body">
      <el-aside
        class="admin-sidebar"
        :class="{ 'mobile-open': mobileMenuOpen }"
        :width="isMobile ? '264px' : (asideCollapsed ? '72px' : '248px')"
      >
        <AsideMenu v-model="asideCollapsed" :mobile="isMobile" @navigate="mobileMenuOpen = false" />
      </el-aside>
      <button v-if="mobileMenuOpen" class="sidebar-scrim" type="button" aria-label="关闭运营导航" @click="mobileMenuOpen = false"></button>
      <el-main class="admin-main">
        <HomeBreadcrumb />
        <RouterView />
      </el-main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AsideMenu from '../components/serve/home/AsideMenu.vue'
import HomeBreadcrumb from '../components/serve/home/HomeBreadcrumb.vue'
import HomeHeader from '../components/serve/home/HomeHeader.vue'

const route = useRoute()
const asideCollapsed = ref(false)
const mobileMenuOpen = ref(false)
const isMobile = ref(false)
let mediaQuery

const updateViewport = (event) => {
  isMobile.value = event.matches
  if (!event.matches) mobileMenuOpen.value = false
}

watch(() => route.fullPath, () => { mobileMenuOpen.value = false })
watch(mobileMenuOpen, (open) => { document.body.style.overflow = open ? 'hidden' : '' })

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 900px)')
  updateViewport(mediaQuery)
  mediaQuery.addEventListener('change', updateViewport)
})
onUnmounted(() => {
  mediaQuery?.removeEventListener('change', updateViewport)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.admin-shell { min-height: 100vh; padding-top: var(--sp-header-height); background: transparent; }
.admin-body { min-height: calc(100vh - var(--sp-header-height)); display: flex; align-items: stretch; }
.admin-sidebar { position: sticky; z-index: 20; top: var(--sp-header-height); height: calc(100vh - var(--sp-header-height)); flex: 0 0 auto; overflow: hidden; background: #f8f8fa; border-right: 1px solid rgb(0 0 0 / 8%); transition: width var(--sp-duration-base) var(--sp-ease-standard), transform var(--sp-duration-base) var(--sp-ease-standard); }
.admin-main { min-width: 0; padding: 20px 24px 34px; overflow: visible; background: transparent; }
.sidebar-scrim { position: fixed; z-index: 15; inset: var(--sp-header-height) 0 0; background: rgb(0 0 0 / 34%); border: 0; backdrop-filter: blur(3px); }
@media (max-width: 900px) {
  .admin-sidebar { position: fixed; z-index: 30; left: 0; bottom: 0; transform: translateX(-105%); }
  .admin-sidebar.mobile-open { transform: translateX(0); }
  .admin-main { width: 100%; padding: 12px 12px 24px; }
}
</style>
