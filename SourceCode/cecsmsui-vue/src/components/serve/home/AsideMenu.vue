<template>
  <nav class="sidebar-container" :class="{ collapsed: isCollapse }" aria-label="运营后台导航">
    <div class="sidebar-heading" v-if="!isCollapse">
      <span>运营中心</span>
      <strong>业务功能</strong>
    </div>
    <button class="collapse-button" type="button" :aria-label="collapseLabel" @click="toggleCollapse">
      <el-icon><Close v-if="mobile" /><Expand v-else-if="isCollapse" /><Fold v-else /></el-icon>
      <span v-if="!isCollapse">{{ mobile ? '关闭菜单' : '收起菜单' }}</span>
    </button>

    <el-skeleton v-if="loading" class="menu-loading" :rows="5" animated />
    <el-menu
      v-else
      :default-active="route.path"
      class="sidebar-menu"
      :collapse="isCollapse"
      :collapse-transition="false"
      router
      @select="emit('navigate')"
    >
      <el-sub-menu v-for="menu in rootMenus" :key="menu.id" :index="String(menu.id)">
        <template #title>
          <el-icon><component :is="getIcon(menu.icon)" /></el-icon>
          <span>{{ menu.name }}</span>
        </template>
        <el-menu-item v-for="sub in childrenFor(menu.id)" :key="sub.id" :index="sub.path">
          <span>{{ sub.name }}</span>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
    <p v-if="!loading && !rootMenus.length && !isCollapse" class="menu-empty">当前角色暂无可用菜单</p>
  </nav>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Bicycle, Bowl, Brush, Close, Expand, Fold, Food, GoldMedal, Operation } from '@element-plus/icons-vue'
import { useMenuStore } from '../../../stores/menu'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mobile: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'navigate'])
const route = useRoute()
const menuStore = useMenuStore()
const loading = ref(true)
const isCollapse = computed({
  get: () => props.mobile ? false : props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const LOCAL_AGENT_MENU_ID = -100
const rootMenus = computed(() => {
  const menus = menuStore.getChildren4me(1)
  const hasAgentMenu = menuStore.menus4me.some((item) => String(item.path || '').includes('AgentOperationsView'))
  if (Number(menuStore.formPassenger?.roleId) === 1 && !hasAgentMenu) {
    return [...menus, { id: LOCAL_AGENT_MENU_ID, name: '智能服务运营', icon: 'Operation' }]
  }
  return menus
})
const childrenFor = (id) => id === LOCAL_AGENT_MENU_ID
  ? [{ id: -101, name: '小伴运营中心', path: '/AgentOperationsView' }]
  : menuStore.getChildren4me(id)
const collapseLabel = computed(() => props.mobile ? '关闭运营导航' : (isCollapse.value ? '展开运营导航' : '收起运营导航'))
const icons = { GoldMedal, Brush, Bicycle, Bowl, Operation, Food }
const getIcon = (icon) => icons[icon] || Operation

const toggleCollapse = () => {
  if (props.mobile) emit('navigate')
  else isCollapse.value = !isCollapse.value
}

onMounted(async () => {
  try { await menuStore.load4MeByLists() }
  finally { loading.value = false }
})
</script>

<style scoped>
.sidebar-container { height: 100%; display: flex; flex-direction: column; overflow: hidden auto; color: var(--sp-text); background: rgb(248 248 250 / 96%); border-right: 1px solid rgb(0 0 0 / 8%); }
.sidebar-heading { display: grid; gap: 3px; padding: 24px 18px 9px; }
.sidebar-heading span { color: var(--sp-text-muted); font-size: 10px; font-weight: 600; letter-spacing: .04em; }
.sidebar-heading strong { color: var(--sp-text); font-size: 17px; font-weight: 700; letter-spacing: -.02em; }
.collapse-button { min-height: 44px; display: flex; align-items: center; justify-content: center; gap: 8px; margin: 10px 12px; padding: 0 11px; color: var(--sp-text-secondary); font-size: 12px; font-weight: 600; background: #fff; border: 1px solid rgb(0 0 0 / 9%); border-radius: var(--sp-radius-pill); cursor: pointer; transition: color var(--sp-duration-fast) ease, background-color var(--sp-duration-fast) ease, border-color var(--sp-duration-fast) ease; }
.collapse-button:hover { color: var(--sp-brand-strong); background: var(--sp-brand-soft); border-color: #b7d5f3; }
.menu-loading { padding: 12px 16px; }
.sidebar-menu { flex: 1; border: 0; background: transparent; }
.sidebar-menu :deep(.el-menu-item), .sidebar-menu :deep(.el-sub-menu__title) { min-height: 48px; margin: 3px 10px; color: var(--sp-text-secondary); font-size: 13px; font-weight: 550; border-radius: var(--sp-radius-sm); transition: color var(--sp-duration-fast) ease, background-color var(--sp-duration-fast) ease; }
.sidebar-menu :deep(.el-menu-item:hover), .sidebar-menu :deep(.el-sub-menu__title:hover) { color: var(--sp-text); background: rgb(0 0 0 / 4%); }
.sidebar-menu :deep(.el-menu-item.is-active) { color: var(--sp-brand-strong); font-weight: 650; background: var(--sp-brand-soft); }
.sidebar-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title) { color: var(--sp-text); }
.sidebar-menu.el-menu--collapse :deep(.el-sub-menu__title) { justify-content: center; padding: 0 !important; }
.menu-empty { margin: 12px; padding: 14px; color: var(--sp-text-muted); font-size: 12px; line-height: 1.6; text-align: center; background: #fff; border: 1px solid var(--sp-border); border-radius: var(--sp-radius-sm); }
</style>
