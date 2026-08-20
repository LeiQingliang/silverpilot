<template>
  <nav class="workspace-breadcrumb" aria-label="当前位置">
    <el-breadcrumb :separator-icon="ArrowRight">
      <el-breadcrumb-item>运营工作台</el-breadcrumb-item>
      <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="`${item}-${index}`">
        {{ item }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { useMenuStore } from '../../../stores/menu'

const route = useRoute()
const menuStore = useMenuStore()
const fallbackTitles = {
  IndexView: ['数据总览'],
  ForumManageView: ['内容运营', '留言管理'],
  RecipeManageView: ['健康内容', '菜谱管理'],
  RecipeOrderManageView: ['健康内容', '菜谱订单'],
  RecipeEditView: ['健康内容', '菜谱编辑'],
  AgentOperationsView: ['智能服务运营', '小伴运行与知识协同']
}

const breadcrumbs = computed(() => {
  const fromStore = menuStore.getCrmbreadcrumbByPageName
  if (Array.isArray(fromStore) && fromStore.length) return fromStore
  return fallbackTitles[route.name] || [String(route.meta?.title || '业务页面')]
})
</script>

<style scoped>
.workspace-breadcrumb {
  min-height: 42px;
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  padding: 0 4px;
  background: transparent;
}
.workspace-breadcrumb :deep(.el-breadcrumb__inner) { color: var(--sp-text-muted); font-size: 12px; font-weight: 550; }
.workspace-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) { color: var(--sp-text); font-weight: 650; }
</style>
