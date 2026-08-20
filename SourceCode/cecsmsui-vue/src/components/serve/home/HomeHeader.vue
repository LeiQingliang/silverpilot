<template>
  <header class="home-header">
    <div class="header-left">
      <button class="mobile-nav-button" type="button" aria-label="打开运营导航" @click="emit('toggle-navigation')">
        <el-icon><Menu /></el-icon>
      </button>
      <button class="brand-button" type="button" aria-label="返回运营首页" @click="goHome">
        <img src="../../../assets/img/logo.png" alt="" />
        <span><small>社区养老服务</small><strong>智慧养老运营中心</strong></span>
      </button>
    </div>

    <div class="header-right">
      <span class="system-signal"><i aria-hidden="true"></i>业务系统</span>
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="user-button" type="button" aria-label="打开账号菜单">
          <span class="avatar" aria-hidden="true">{{ userInitial }}</span>
          <span class="user-copy"><strong>{{ username }}</strong><small>{{ roleLabel }}</small></span>
          <el-icon aria-hidden="true"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="home">返回工作台</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Menu } from '@element-plus/icons-vue'

const emit = defineEmits(['toggle-navigation'])
const router = useRouter()
const storedUser = sessionStorage.getItem('user') || localStorage.getItem('user')
const user = ref({})
try { user.value = storedUser ? JSON.parse(storedUser) : {} } catch { user.value = {} }

const username = computed(() => user.value.name || user.value.username || '当前用户')
const userInitial = computed(() => username.value.trim().charAt(0).toUpperCase() || '用')
const roleLabel = computed(() => ({ 1: '系统管理员', 2: '社区工作者', 3: '医护人员' }[Number(user.value.roleId)] || '业务成员'))
const homeByRole = { 1: '/IndexView', 2: '/ActivityMenageView', 3: '/HealthOrderView' }
const goHome = () => router.push(homeByRole[Number(user.value.roleId)] || '/login')

const handleCommand = (command) => {
  if (command === 'home') return goHome()
  if (command === 'logout') {
    for (const storage of [sessionStorage, localStorage]) {
      for (const key of ['token', 'user', 'id', 'roleId', 'birthday', 'isNotified']) storage.removeItem(key)
    }
    router.push('/login')
  }
}
</script>

<style scoped>
.home-header {
  position: fixed;
  z-index: 1300;
  top: 0;
  left: 0;
  width: 100%;
  height: var(--sp-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
  color: var(--sp-text);
  background: rgb(250 250 252 / 88%);
  border-bottom: 1px solid rgb(0 0 0 / 8%);
  backdrop-filter: blur(24px) saturate(1.5);
}
.header-left, .header-right { display: flex; align-items: center; gap: 10px; min-width: 0; }
.brand-button { min-height: 44px; display: flex; align-items: center; gap: 9px; padding: 0; text-align: left; background: transparent; border: 0; border-radius: var(--sp-radius-xs); cursor: pointer; }
.brand-button img { width: auto; height: 34px; opacity: .96; }
.brand-button > span { display: grid; gap: 1px; }
.brand-button small { color: var(--sp-text-muted); font-size: 9px; font-weight: 550; letter-spacing: .01em; }
.brand-button strong { color: var(--sp-text); font-size: 14px; font-weight: 700; letter-spacing: -.02em; }
.system-signal { min-height: 34px; display: inline-flex; align-items: center; gap: 7px; padding: 0 11px; color: var(--sp-text-muted); font-size: 11px; font-weight: 600; background: rgb(255 255 255 / 70%); border: 1px solid rgb(0 0 0 / 9%); border-radius: var(--sp-radius-pill); }
.system-signal i { width: 7px; height: 7px; background: var(--sp-success); border-radius: 50%; animation: sp-signal-pulse 2.8s ease-in-out infinite; }
.user-button { min-height: 42px; display: flex; align-items: center; gap: 9px; padding: 3px 11px 3px 4px; color: var(--sp-text-secondary); background: rgb(255 255 255 / 74%); border: 1px solid rgb(0 0 0 / 10%); border-radius: var(--sp-radius-pill); cursor: pointer; transition: background-color var(--sp-duration-fast) ease, border-color var(--sp-duration-fast) ease; }
.user-button:hover { color: var(--sp-text); background: #fff; border-color: rgb(0 0 0 / 16%); }
.avatar { width: 34px; height: 34px; display: grid; place-items: center; color: var(--sp-on-brand); font-size: 12px; font-weight: 700; background: var(--sp-brand); border-radius: 50%; }
.user-copy { min-width: 76px; display: grid; gap: 1px; text-align: left; line-height: 1.2; }
.user-copy strong { max-width: 100px; overflow: hidden; color: var(--sp-text); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.user-copy small { color: var(--sp-text-muted); font-size: 9px; }
.mobile-nav-button { width: 44px; height: 44px; display: none; place-items: center; color: var(--sp-text); background: #fff; border: 1px solid rgb(0 0 0 / 10%); border-radius: var(--sp-radius-pill); cursor: pointer; }
@media (max-width: 900px) { .home-header { padding-inline: 12px; }.mobile-nav-button { display: grid; }.brand-button img { height: 32px; }.brand-button > span, .system-signal, .user-copy { display: none; }.user-button { width: 44px; padding: 4px; justify-content: center; }.user-button > .el-icon { display: none; } }
</style>
