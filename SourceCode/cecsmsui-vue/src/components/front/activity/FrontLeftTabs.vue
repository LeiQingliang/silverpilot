<template>
  <section class="catalog-page" aria-labelledby="activity-catalog-title">
    <header class="catalog-heading">
      <div><span class="sp-eyebrow">社区活动安排</span><h1 id="activity-catalog-title" class="sp-page-title">社区活动</h1><p class="sp-page-copy">按活动类型浏览真实社区安排，查看日期与详情后再完成报名。</p></div>
      <span class="catalog-count">{{ total }} 项活动</span>
    </header>

    <div class="catalog-panel">
      <el-tabs :tab-position="isMobile ? 'top' : 'left'" class="catalog-tabs">
        <el-tab-pane label="全部">
          <template #label><el-icon><Star /></el-icon><span>全部</span></template>
          <div v-if="tableData.length" class="card-container">
            <el-card
              v-for="activity in tableData"
              :key="activity.id"
              class="catalog-card"
              shadow="never"
              role="button"
              tabindex="0"
              :aria-label="`查看活动：${activity.activityName}`"
              @click="toDetail(activity.id, activity.image)"
              @keydown.enter="toDetail(activity.id, activity.image)"
              @keydown.space.prevent="toDetail(activity.id, activity.image)"
            >
              <SmartImage :src="activity.image" :alt="activity.activityName" fit="cover" fallback-label="活动图片" class="catalog-image" />
              <div class="card-info"><strong>{{ activity.activityName }}</strong><time>{{ activity.activityDate || '日期待定' }}</time></div>
            </el-card>
          </div>
          <el-empty v-else description="暂时没有可报名活动" />
        </el-tab-pane>

        <el-tab-pane v-for="item in state1Type" :key="item.id" :label="item.type" :name="item.id.toString()">
          <template #label><el-icon><component :is="getIcon(item.id % 5)" /></el-icon><span>{{ item.type }}</span></template>
          <div v-if="categorizedActivities[item.id]?.length" class="card-container">
            <el-card
              v-for="activity in categorizedActivities[item.id]"
              :key="activity.id"
              class="catalog-card"
              shadow="never"
              role="button"
              tabindex="0"
              :aria-label="`查看活动：${activity.activityName}`"
              @click="toDetail(activity.id, activity.image)"
              @keydown.enter="toDetail(activity.id, activity.image)"
              @keydown.space.prevent="toDetail(activity.id, activity.image)"
            >
              <SmartImage :src="activity.image" :alt="activity.activityName" fit="cover" fallback-label="活动图片" class="catalog-image" />
              <div class="card-info"><strong>{{ activity.activityName }}</strong><time>{{ activity.activityDate || '日期待定' }}</time></div>
            </el-card>
          </div>
          <el-empty v-else description="该分类暂无活动" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="pagination-panel">
      <el-config-provider :locale="locale">
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="total" :pager-count="isMobile ? 5 : 9" background layout="prev, pager, next, jumper, total" @current-change="handleCurrentChange" />
      </el-config-provider>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, GoldMedal, Guide, Operation, Star, VideoCameraFilled } from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { storeToRefs } from 'pinia'
import { useActivityStore } from '../../../stores/activity.js'
import { useActivitytypeStore } from '../../../stores/activitytype.js'
import SmartImage from '@/components/common/SmartImage.vue'

const locale = computed(() => zhCn)
const activityStore = useActivityStore()
const activityTypeStore = useActivitytypeStore()
const { tableData, total } = storeToRefs(activityStore)
const { state1Type, categorizedActivities } = storeToRefs(activityTypeStore)
const pageSize = ref(6)
const currentPage = ref(1)
const isMobile = ref(false)
let mediaQuery
let updateViewport

const handleCurrentChange = () => {
  activityStore.currentPage = currentPage.value
  activityStore.pageSize = pageSize.value
  activityStore.selectAllByPage()
}

onMounted(async () => {
  mediaQuery = window.matchMedia('(max-width: 760px)')
  updateViewport = (event) => { isMobile.value = event.matches }
  updateViewport(mediaQuery)
  mediaQuery.addEventListener('change', updateViewport)
  activityStore.currentPage = currentPage.value
  activityStore.pageSize = pageSize.value
  await Promise.all([activityTypeStore.loadByState(), activityStore.selectAllByPage()])
})
onUnmounted(() => mediaQuery?.removeEventListener('change', updateViewport))

const router = useRouter()
const toDetail = (id, image) => {
  activityStore.activityDetail.id = id
  activityStore.activityDetail.image = image
  router.push({ path: '/front/activity/ActivityDetailView', query: { Id: id } })
}
const getIcon = (id) => ({ 1: GoldMedal, 2: VideoCameraFilled, 3: Guide, 4: Connection, 0: Operation }[id] || Star)
</script>

<style scoped>
.catalog-page { width: min(var(--sp-content-max), calc(100% - 40px)); min-height: calc(100vh - var(--sp-header-height)); margin: 0 auto; padding: 34px 0 48px; }
.catalog-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.catalog-count { flex: 0 0 auto; padding: 7px 10px; color: var(--sp-brand-strong); font-size: 11px; font-weight: 750; background: var(--sp-brand-soft); border: 1px solid color-mix(in srgb, var(--sp-brand) 24%, var(--sp-border)); border-radius: var(--sp-radius-pill); }
.catalog-panel { min-height: 520px; padding: 18px; background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(22px); }
.catalog-tabs :deep(.el-tabs__header.is-left) { width: 184px; margin-right: 20px; padding: 7px; background: var(--sp-gradient-night); border: 1px solid rgb(255 255 255 / 9%); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-md); }
.catalog-tabs :deep(.el-tabs__item) { min-height: 46px; gap: 8px; margin: 3px; padding: 0 13px !important; color: var(--sp-on-night-muted); font-weight: 650; border-radius: var(--sp-radius-sm); }
.catalog-tabs :deep(.el-tabs__item:hover) { color: var(--sp-on-night); background: rgb(255 255 255 / 8%); }
.catalog-tabs :deep(.el-tabs__item.is-active) { color: var(--sp-night); background: var(--sp-energy-lime); box-shadow: 0 9px 22px rgb(0 0 0 / 20%); }
.catalog-tabs :deep(.el-tabs__active-bar), .catalog-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.catalog-tabs :deep(.el-tabs__content) { min-height: 480px; overflow: visible; }
.card-container { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; padding: 2px; }
.catalog-card { overflow: hidden; cursor: pointer; transition: transform var(--sp-duration-base) var(--sp-ease-standard), box-shadow var(--sp-duration-base) ease, border-color var(--sp-duration-base) ease; }
.catalog-card:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--sp-brand) 35%, var(--sp-border)); box-shadow: var(--sp-shadow-md); }
.catalog-card :deep(.el-card__body) { padding: 0; }.catalog-card .catalog-image { width: 100%; height: 190px; display: block; background: var(--sp-surface-muted); }
.card-info { display: grid; gap: 7px; padding: 14px 15px 16px; }.card-info strong { overflow: hidden; color: var(--sp-text); font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }.card-info time { color: var(--sp-text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.pagination-panel { display: flex; justify-content: center; margin-top: 18px; padding: 13px; background: var(--sp-surface); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
@media (max-width: 1050px) { .card-container { grid-template-columns: 1fr 1fr; } }
@media (max-width: 760px) { .catalog-page { width: calc(100% - 24px); padding-top: 22px; }.catalog-heading { align-items: flex-start; flex-direction: column; }.catalog-panel { min-height: 0; padding: 10px; }.catalog-tabs :deep(.el-tabs__header.is-top) { margin: 0 0 14px; padding: 6px; background: var(--sp-gradient-night); border-radius: var(--sp-radius-md); }.catalog-tabs :deep(.el-tabs__nav-scroll) { overflow-x: auto; }.catalog-tabs :deep(.el-tabs__nav) { min-width: max-content; }.catalog-tabs :deep(.el-tabs__content) { min-height: 0; }.card-container { grid-template-columns: 1fr; }.catalog-card .catalog-image { height: 210px; } }
</style>
