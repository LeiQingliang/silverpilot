<template>
  <header
    id="frontheader"
    :class="{
      'agent-mode': route.path === '/front/ai/AiChat',
      'hero-mode': showHeroHeader
    }"
  >
    <div class="header-wrapper">
      <button class="brand-button" type="button" aria-label="返回智慧养老首页" @click="goHome">
        <img src="../../../assets/img/logo.png" alt="" />
        <span class="brand-copy"><strong>SilverPilot</strong><small>智慧养老服务平台</small></span>
      </button>

      <nav v-if="isLoggedIn" id="primary-navigation" class="menu" :class="{ open: mobileOpen }" aria-label="主要导航">
        <el-menu
          :default-active="activeIndex"
          :mode="isMobile ? 'vertical' : 'horizontal'"
          :ellipsis="false"
          router
          @select="closeMobileMenu"
        >
          <el-menu-item v-for="item in navigation" :key="item.path" :index="item.path">
            <span v-if="item.agent" class="agent-nav-dot" aria-hidden="true"></span>
            {{ item.label }}
          </el-menu-item>
        </el-menu>
      </nav>

      <div class="header-utilities">
        <div v-if="!isLoggedIn && !isAuthenticationPage" class="auth-buttons">
          <el-button class="login-btn" @click="goLogin">登录</el-button>
          <el-button type="primary" class="register-btn" @click="goRegister">注册</el-button>
        </div>
        <el-dropdown v-else-if="isLoggedIn" class="selectmenu" trigger="click" @command="handleDropdown">
          <button class="user-trigger" type="button" aria-label="打开个人菜单">
            <span class="user-avatar" aria-hidden="true">{{ userInitial }}</span>
            <span class="user-name">{{ username }}</span>
            <el-icon aria-hidden="true"><ChevronDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in accountNavigation" :key="item.command" :command="item.command">
                {{ item.label }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button
          v-if="isLoggedIn"
          class="nav-trigger"
          type="button"
          :aria-expanded="mobileOpen"
          aria-controls="primary-navigation"
          :aria-label="mobileOpen ? '关闭导航菜单' : '打开导航菜单'"
          @click="mobileOpen = !mobileOpen"
        >
          <el-icon><X v-if="mobileOpen" /><Menu v-else /></el-icon>
        </button>
      </div>
    </div>
    <button v-if="mobileOpen" class="nav-scrim" type="button" aria-label="关闭导航菜单" @click="closeMobileMenu"></button>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, Menu, X } from '@lucide/vue'

const router = useRouter()
const route = useRoute()
const activeIndex = ref(route.path)
const isLoggedIn = ref(false)
const username = ref('')
const mobileOpen = ref(false)
const isMobile = ref(false)
const heroHasEnded = ref(false)
let mediaQuery

const showHeroHeader = computed(() => route.name === 'FrontHomeView' && !heroHasEnded.value)
const isAuthenticationPage = computed(() => route.name === 'login' || route.name === 'register')

const navigation = [
  { path: '/front/home/FrontHomeView', label: '首页' },
  { path: '/front/activity/FrontActivityView', label: '活动' },
  { path: '/front/service/FrontServiceView', label: '服务' },
  { path: '/front/recipe/RecipeListView', label: '健康菜谱' },
  { path: '/front/forum/ForumHomeView', label: '社区' },
  { path: '/front/ai/AiChat', label: '小伴生活助理', agent: true },
  { path: '/front/personal/PersonalCenter', label: '个人中心' }
]

const accountNavigation = [
  { command: 'MyActivityView', label: '活动记录' },
  { command: 'MyServiceView', label: '服务订单' },
  { command: 'MyReportView', label: '体检报告' },
  { command: 'MyCommentView', label: '我的留言' },
  { command: 'MyRecipeOrders', label: '菜谱预订' }
]

const routes = {
  MyActivityView: '/front/myActivity/MyActivityView',
  MyServiceView: '/front/myService/MyServiceView',
  MyReportView: '/front/myReport/MyReportView',
  MyCommentView: '/front/myComment/MyCommentView',
  MyRecipeOrders: '/front/recipe/MyRecipeOrders'
}

const userInitial = computed(() => (username.value.trim().charAt(0) || '用').toUpperCase())

const checkLoginStatus = () => {
  const userStr = sessionStorage.getItem('user') || localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      if (user?.username) {
        isLoggedIn.value = true
        username.value = user.name || user.username
        return
      }
    } catch (error) {
      console.error('解析用户信息失败:', error)
    }
  }
  isLoggedIn.value = false
  username.value = ''
}

const updateViewport = (event) => {
  isMobile.value = event.matches
  if (!event.matches) mobileOpen.value = false
}

const closeMobileMenu = () => { mobileOpen.value = false }
const goHome = () => router.push(isLoggedIn.value ? '/front/home/FrontHomeView' : '/login')
const goLogin = () => router.push('/login')
const goRegister = () => router.push('/register')

const handleDropdown = (command) => {
  if (command === 'logout') {
    for (const storage of [sessionStorage, localStorage]) {
      for (const key of ['token', 'user', 'id', 'roleId', 'birthday', 'isNotified']) storage.removeItem(key)
    }
    checkLoginStatus()
    router.push('/login')
    return
  }
  if (routes[command]) router.push(routes[command])
}

const updateHeroHeader = () => {
  if (route.name !== 'FrontHomeView') {
    heroHasEnded.value = false
    return
  }
  const hero = document.querySelector('.product-hero')
  heroHasEnded.value = Boolean(hero && hero.getBoundingClientRect().bottom <= 76)
}

watch(() => route.path, (newPath) => {
  activeIndex.value = newPath
  closeMobileMenu()
  window.requestAnimationFrame(updateHeroHeader)
})

watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

onMounted(() => {
  checkLoginStatus()
  window.addEventListener('storage', checkLoginStatus)
  window.addEventListener('scroll', updateHeroHeader, { passive: true })
  window.addEventListener('resize', updateHeroHeader)
  mediaQuery = window.matchMedia('(max-width: 980px)')
  updateViewport(mediaQuery)
  mediaQuery.addEventListener('change', updateViewport)
  window.requestAnimationFrame(updateHeroHeader)
})

onUnmounted(() => {
  window.removeEventListener('storage', checkLoginStatus)
  window.removeEventListener('scroll', updateHeroHeader)
  window.removeEventListener('resize', updateHeroHeader)
  mediaQuery?.removeEventListener('change', updateViewport)
  document.body.style.overflow = ''
})
</script>

<style scoped>
#frontheader {
  position: fixed;
  z-index: 1100;
  top: 0;
  left: 0;
  width: 100%;
  height: var(--sp-header-height);
  display: flex;
  align-items: center;
  background: rgb(250 250 252 / 82%);
  border-bottom: 1px solid rgb(0 0 0 / 8%);
  backdrop-filter: blur(24px) saturate(1.5);
  transition: height .3s var(--sp-ease-expressive), background-color .3s ease, border-color .3s ease;
}
.header-wrapper { position: relative; z-index: 3; width: min(1280px, 100%); height: 100%; display: flex; align-items: center; gap: 18px; margin: 0 auto; padding: 0 22px; }
.brand-button { flex: 0 0 auto; min-height: 44px; display: inline-flex; align-items: center; gap: 8px; padding: 2px 6px 2px 0; background: transparent; border: 0; border-radius: var(--sp-radius-xs); cursor: pointer; }
.brand-button img { width: auto; height: 34px; display: block; }
.brand-copy { display: grid; gap: 1px; text-align: left; line-height: 1.15; }
.brand-copy strong { color: var(--sp-text); font-size: 13px; font-weight: 700; letter-spacing: -.02em; }
.brand-copy small { color: var(--sp-text-muted); font-size: 9px; letter-spacing: .01em; }
.menu { flex: 1; min-width: 0; }
.menu :deep(.el-menu.el-menu--horizontal) { height: var(--sp-header-height); border: 0; background: transparent; }
.menu :deep(.el-menu-item) { height: 38px; align-self: center; padding: 0 11px; color: var(--sp-text-secondary); font-size: 12px; font-weight: 550; border: 0 !important; border-radius: var(--sp-radius-pill); transition: color var(--sp-duration-fast) ease, background-color var(--sp-duration-fast) ease; }
.menu :deep(.el-menu-item:hover) { color: var(--sp-text); background: rgb(0 0 0 / 4%) !important; }
.menu :deep(.el-menu-item.is-active) { color: var(--sp-brand-strong) !important; background: var(--sp-brand-soft) !important; }
.agent-nav-dot { width: 6px; height: 6px; margin-right: 7px; background: var(--sp-brand); border-radius: 50%; animation: sp-signal-pulse 2.8s ease-in-out infinite; }
.header-utilities { flex: 0 0 auto; display: flex; align-items: center; gap: 9px; }
.auth-buttons { display: flex; gap: 8px; }
.login-btn, .register-btn { min-height: 38px; border-radius: var(--sp-radius-pill); }
.user-trigger { min-height: 42px; display: flex; align-items: center; gap: 8px; padding: 3px 12px 3px 4px; color: var(--sp-text-secondary); background: rgb(255 255 255 / 72%); border: 1px solid rgb(0 0 0 / 10%); border-radius: var(--sp-radius-pill); cursor: pointer; transition: background-color var(--sp-duration-fast) ease, border-color var(--sp-duration-fast) ease; }
.user-trigger:hover { color: var(--sp-text); background: #fff; border-color: rgb(0 0 0 / 16%); }
.user-avatar { width: 34px; height: 34px; display: grid; place-items: center; color: var(--sp-on-brand); font-size: 12px; font-weight: 700; background: var(--sp-brand); border-radius: 50%; }
.user-name { max-width: 88px; overflow: hidden; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.nav-trigger { width: 44px; height: 44px; display: none; place-items: center; color: var(--sp-text); background: rgb(255 255 255 / 72%); border: 1px solid rgb(0 0 0 / 10%); border-radius: var(--sp-radius-pill); cursor: pointer; }
.nav-trigger .el-icon { font-size: 20px; }
.nav-scrim { position: fixed; z-index: 1; inset: var(--sp-header-height) 0 0; width: 100%; height: calc(100vh - var(--sp-header-height)); background: rgb(0 0 0 / 32%); border: 0; backdrop-filter: blur(3px); }

#frontheader.hero-mode {
  height: 72px;
  background: transparent;
  border-bottom-color: transparent;
  backdrop-filter: none;
}

.hero-mode .header-wrapper { width: 100%; max-width: none; padding-inline: 20px; }
.hero-mode .brand-button img { height: 26px; filter: brightness(0) invert(1); }
.hero-mode .brand-copy { display: block; }
.hero-mode .brand-copy strong {
  color: #fff;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 24px;
  font-style: italic;
  font-weight: 500;
}
.hero-mode .brand-copy small { display: none; }
.hero-mode .user-trigger { color: #1d1d1f; background: #fff; border-color: #fff; }
.hero-mode .user-trigger:hover { color: #111; background: #f5f5f7; border-color: #f5f5f7; }
.hero-mode .user-avatar { color: #fff; background: #1d1d1f; }
.hero-mode .nav-trigger { color: #fff; background: rgb(255 255 255 / 16%); border-color: rgb(255 255 255 / 30%); backdrop-filter: blur(16px); }

@media (min-width: 981px) {
  .hero-mode .menu {
    position: absolute;
    left: 50%;
    flex: 0 0 auto;
    padding: 7px;
    background: rgb(255 255 255 / 18%);
    border: 1px solid rgb(255 255 255 / 28%);
    border-radius: var(--sp-radius-pill);
    transform: translateX(-50%);
    backdrop-filter: blur(18px) saturate(1.2);
  }
  .hero-mode .menu :deep(.el-menu.el-menu--horizontal) { height: auto; }
  .hero-mode .menu :deep(.el-menu-item) { height: 34px; color: rgb(255 255 255 / 82%); border-radius: var(--sp-radius-pill); }
  .hero-mode .menu :deep(.el-menu-item:hover) { color: #fff; background: rgb(255 255 255 / 18%) !important; }
  .hero-mode .menu :deep(.el-menu-item.is-active) { color: #fff !important; background: rgb(255 255 255 / 22%) !important; }
  .hero-mode .agent-nav-dot { background: #fff; }
  .hero-mode .header-utilities { margin-left: auto; }
}

@media (max-width: 1180px) {
  .brand-copy { display: none; }
  .header-wrapper { gap: 12px; }
  .menu :deep(.el-menu-item) { padding: 0 8px; font-size: 11px; }
}

@media (max-width: 980px) {
  .header-wrapper { padding-inline: 14px; }
  .brand-button img { height: 32px; }
  .nav-trigger { display: grid; }
  .menu {
    position: fixed;
    z-index: 2;
    top: var(--sp-header-height);
    right: 12px;
    left: 12px;
    overflow: hidden;
    opacity: 0;
    visibility: hidden;
    transform: translateY(-10px);
    background: rgb(255 255 255 / 96%);
    border: 1px solid rgb(0 0 0 / 9%);
    border-radius: 0 0 var(--sp-radius-md) var(--sp-radius-md);
    box-shadow: var(--sp-shadow-lg);
    backdrop-filter: blur(24px) saturate(1.25);
    transition: opacity var(--sp-duration-fast) ease, transform var(--sp-duration-fast) ease, visibility var(--sp-duration-fast) ease;
  }
  .menu.open { opacity: 1; visibility: visible; transform: translateY(0); }
  .menu :deep(.el-menu) { max-height: calc(100vh - var(--sp-header-height) - 24px); display: grid; grid-template-columns: 1fr 1fr; gap: 5px; overflow-y: auto; padding: 10px; border: 0; background: transparent; }
  .menu :deep(.el-menu-item) { width: 100%; min-height: 48px; justify-content: flex-start; margin: 0; padding: 0 14px !important; font-size: 14px; }
  #frontheader.hero-mode { height: 64px; }
  .hero-mode .menu { top: 64px; background: rgb(10 15 20 / 96%); border-color: rgb(255 255 255 / 14%); }
  .hero-mode .menu :deep(.el-menu-item) { color: rgb(255 255 255 / 82%); }
  .hero-mode .menu :deep(.el-menu-item:hover) { color: #fff; background: rgb(255 255 255 / 10%) !important; }
  .hero-mode .menu :deep(.el-menu-item.is-active) { color: #fff !important; background: rgb(255 255 255 / 14%) !important; }
}

@media (max-width: 560px) {
  .header-utilities { margin-left: auto; }
  .user-name { display: none; }
  .user-trigger { width: 44px; padding: 4px; justify-content: center; }
  .user-trigger > .el-icon { display: none; }
  .auth-buttons .login-btn { display: none; }
  .auth-buttons .register-btn { padding-inline: 13px; }
  .menu :deep(.el-menu) { grid-template-columns: 1fr; }
}
</style>
