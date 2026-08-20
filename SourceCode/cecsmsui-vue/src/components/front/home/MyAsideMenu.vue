<template>
  <nav class="account-nav" aria-label="个人服务导航">
    <el-menu :default-active="route.path" router>
      <el-menu-item v-for="item in items" :key="item.path" :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </el-menu-item>
    </el-menu>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { Calendar, ChatDotSquare, DocumentChecked, Food, Service } from '@element-plus/icons-vue'

const route = useRoute()
const items = [
  { path: '/front/myActivity/MyActivityView', label: '活动记录', icon: Calendar },
  { path: '/front/myService/MyServiceView', label: '服务订单', icon: Service },
  { path: '/front/myReport/MyReportView', label: '体检报告', icon: DocumentChecked },
  { path: '/front/myComment/MyCommentView', label: '我的留言', icon: ChatDotSquare },
  { path: '/front/recipe/MyRecipeOrders', label: '菜谱预订', icon: Food }
]
</script>

<style scoped>
.account-nav { padding: 10px; }
.account-nav :deep(.el-menu) { border: 0; background: transparent; }
.account-nav :deep(.el-menu-item) {
  min-height: 48px;
  gap: 9px;
  margin: 3px 0;
  padding: 0 13px !important;
  color: var(--sp-text-secondary);
  font-weight: 550;
  border-radius: var(--sp-radius-sm);
}
.account-nav :deep(.el-menu-item:hover) { color: var(--sp-text); background: rgb(0 0 0 / 4%); }
.account-nav :deep(.el-menu-item.is-active) { color: var(--sp-brand-strong); font-weight: 650; background: var(--sp-brand-soft); }

@media (max-width: 900px) {
  .account-nav { min-width: max-content; padding: 7px; }
  .account-nav :deep(.el-menu) { display: flex; }
  .account-nav :deep(.el-menu-item) { min-width: max-content; margin: 0 2px; }
}
</style>
