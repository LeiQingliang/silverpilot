<script setup>
import { computed, onBeforeUnmount, onErrorCaptured, onMounted, ref, watch } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { RouterView, useRoute } from 'vue-router'
import GlobalFooter from './components/serve/home/GlobalFooter.vue'
import router from './router'

const route = useRoute()
const isNavigating = ref(false)
const renderError = ref('')
const isOffline = ref(globalThis.navigator?.onLine === false)
const messageConfig = Object.freeze({
  duration: 3600,
  grouping: true,
  max: 2,
  offset: 18,
  showClose: true
})
const showFooter = computed(() => (
  route.path.startsWith('/front/') && !route.path.startsWith('/front/ai/')
))
const routeFamily = computed(() => {
  if (route.path === '/login' || route.path === '/register') return 'auth'
  if (route.path.startsWith('/front/ai/')) return 'assistant'
  if (route.matched.some((record) => record.name === 'home')) return 'admin'
  if (route.matched.some((record) => record.name === 'MyView')) return 'account'
  if (route.path.startsWith('/front/')) return 'public'
  return 'public'
})

const removeBeforeHook = router.beforeEach(() => {
  isNavigating.value = true
  renderError.value = ''
})
const removeAfterHook = router.afterEach(() => {
  window.requestAnimationFrame(() => { isNavigating.value = false })
})
const removeErrorHook = router.onError((error) => {
  console.error('路由资源加载失败', error)
  renderError.value = isOffline.value
    ? '当前网络已断开，恢复连接后请重新加载。'
    : '页面资源加载失败，可能是版本已更新，请重新加载。'
  isNavigating.value = false
})

const updateOnlineStatus = () => {
  isOffline.value = navigator.onLine === false
}

onMounted(() => {
  window.addEventListener('online', updateOnlineStatus)
  window.addEventListener('offline', updateOnlineStatus)
})

watch(() => route.fullPath, () => {
  renderError.value = ''
})

onErrorCaptured((error) => {
  console.error('页面渲染失败', error)
  renderError.value = '页面暂时无法显示，请刷新后重试。'
  isNavigating.value = false
  return false
})

onBeforeUnmount(() => {
  removeBeforeHook()
  removeAfterHook()
  removeErrorHook()
  window.removeEventListener('online', updateOnlineStatus)
  window.removeEventListener('offline', updateOnlineStatus)
})

const reloadPage = () => window.location.reload()
</script>

<template>
  <el-config-provider :locale="zhCn" :message="messageConfig">
    <div class="app-wrapper">
      <div class="surface-backdrop" aria-hidden="true"></div>
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <div class="route-progress" :class="{ active: isNavigating }" aria-hidden="true"></div>
      <div v-if="isOffline" class="network-status" role="status" aria-live="polite">
        <span aria-hidden="true"></span>网络已断开，已加载页面仍可查看，需要服务器的操作将暂停。
      </div>
      <div id="main-content" class="content-body" tabindex="-1" role="main" :aria-busy="isNavigating">
        <section v-if="renderError" class="route-error" role="alert">
          <span aria-hidden="true">!</span>
          <div>
            <h1>页面加载遇到问题</h1>
            <p>{{ renderError }}</p>
          </div>
          <el-button type="primary" @click="reloadPage">重新加载</el-button>
        </section>
        <RouterView v-slot="{ Component, route }">
          <Transition v-if="!renderError" name="route" mode="out-in">
            <div :key="route.fullPath" class="route-view" :class="`route-family-${routeFamily}`" :data-route-family="routeFamily">
              <component :is="Component" />
            </div>
          </Transition>
        </RouterView>
      </div>
      <GlobalFooter v-if="showFooter" />
    </div>
  </el-config-provider>
</template>

<style>
/* 全局基础样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 外层容器：不干扰顶部固定导航 */
.app-wrapper {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* 内容区域：自动撑开，仅给顶部导航留间距 */
.content-body {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}

.route-view {
  min-width: 0;
  min-height: 100%;
}

.route-progress {
  position: fixed;
  z-index: 10001;
  top: 0;
  left: 0;
  width: 0;
  height: 3px;
  opacity: 0;
  background: var(--sp-gradient-brand);
  box-shadow: 0 2px 14px rgb(0 102 204 / 26%);
  transition: width .32s var(--sp-ease-expressive), opacity .18s ease;
}

.route-progress.active { width: 78%; opacity: 1; }

.network-status {
  position: fixed;
  z-index: 10002;
  top: 12px;
  left: 50%;
  max-width: min(680px, calc(100% - 28px));
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 13px;
  color: var(--sp-warning);
  font-size: 12px;
  font-weight: 650;
  background: var(--sp-warning-soft);
  border: 1px solid color-mix(in srgb, var(--sp-warning) 38%, var(--sp-border));
  border-radius: 999px;
  box-shadow: var(--sp-shadow-md);
  transform: translateX(-50%);
}

.network-status span {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  background: var(--sp-warning);
  border-radius: 50%;
}

.route-error {
  width: min(680px, calc(100% - 32px));
  min-height: 220px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 18px;
  margin: 120px auto 40px;
  padding: 28px;
  color: var(--sp-text);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-lg);
  box-shadow: var(--sp-shadow-md);
}

.route-error > span {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  color: var(--sp-danger);
  font-size: 22px;
  font-weight: 800;
  background: var(--sp-danger-soft);
  border-radius: 14px;
}

.route-error h1 { margin: 0; font-size: 20px; }
.route-error p { margin: 4px 0 0; color: var(--sp-text-secondary); }
.route-error .el-button {
  min-height: 44px;
  padding: 0 16px;
  color: var(--sp-on-brand);
  font-weight: 700;
  background: var(--sp-brand);
  border: 0;
  border-radius: var(--sp-radius-sm);
  cursor: pointer;
}

@media (max-width: 640px) {
  .network-status { top: 7px; width: calc(100% - 20px); justify-content: center; border-radius: 12px; }
  .route-error { grid-template-columns: auto 1fr; }
  .route-error .el-button { grid-column: 1 / -1; }
}
</style>
