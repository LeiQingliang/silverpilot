<template>
  <section class="catalog-page" aria-labelledby="service-catalog-title">
    <header class="catalog-heading">
      <div><span class="sp-eyebrow">社区养老服务</span><h1 id="service-catalog-title" class="sp-page-title">养老服务</h1><p class="sp-page-copy">从服务分类中选择需要的项目，核对服务内容后再提交预约。</p></div>
      <span class="safety-chip">预约前确认</span>
    </header>

    <div class="catalog-panel">
      <el-tabs v-model="activeName" :tab-position="isMobile ? 'top' : 'left'" class="catalog-tabs">
        <el-tab-pane v-for="type in fatherType" :key="type.id" :label="type.serviceName" :name="type.id.toString()">
          <template #label><el-icon><component :is="getIcon(type.id % 4)" /></el-icon><span>{{ type.serviceName }}</span></template>
          <div v-if="categorizedServices[type.id]?.length" class="card-container">
            <el-card
              v-for="service in categorizedServices[type.id]"
              :key="service.id"
              class="catalog-card"
              shadow="never"
              role="button"
              tabindex="0"
              :aria-label="`预约服务：${service.serviceName}`"
              @click="toOrder(type, service)"
              @keydown.enter="toOrder(type, service)"
              @keydown.space.prevent="toOrder(type, service)"
            >
              <SmartImage :src="service.image" :alt="service.serviceName" fit="cover" fallback-label="服务图片" class="catalog-image" />
              <div class="card-info"><strong>{{ service.serviceName }}</strong><span>查看服务与预约信息 <b aria-hidden="true">→</b></span></div>
            </el-card>
          </div>
          <el-empty v-else description="该分类暂无可预约服务" />
        </el-tab-pane>
      </el-tabs>
      <el-empty v-if="!fatherType.length" description="暂时没有可用的服务分类" />
    </div>
    <FrontServiceDialog />
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { Brush, FirstAidKit, GoldMedal, Sunny } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import FrontServiceDialog from './FrontServiceDialog.vue'
import { useServicetypeStore } from '../../../stores/servicetype.js'
import SmartImage from '@/components/common/SmartImage.vue'

const servicetypeStore = useServicetypeStore()
const { fatherType, categorizedServices } = storeToRefs(servicetypeStore)
const activeName = ref('')
const isMobile = ref(false)
let mediaQuery
let updateViewport

onMounted(async () => {
  mediaQuery = window.matchMedia('(max-width: 760px)')
  updateViewport = (event) => { isMobile.value = event.matches }
  updateViewport(mediaQuery)
  mediaQuery.addEventListener('change', updateViewport)
  await servicetypeStore.selectFather()
  if (fatherType.value.length) activeName.value = fatherType.value[0].id.toString()
})
onUnmounted(() => mediaQuery?.removeEventListener('change', updateViewport))

const toOrder = (type, service) => servicetypeStore.pre4Order(type, service)
const getIcon = (id) => ({ 1: GoldMedal, 2: Brush, 3: Sunny, 0: FirstAidKit }[id] || FirstAidKit)
</script>

<style scoped>
.catalog-page { width: min(var(--sp-content-max), calc(100% - 40px)); min-height: calc(100vh - var(--sp-header-height)); margin: 0 auto; padding: 34px 0 48px; }
.catalog-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 22px; }.safety-chip { flex: 0 0 auto; padding: 7px 10px; color: var(--sp-success); font-size: 11px; font-weight: 750; background: var(--sp-success-soft); border: 1px solid color-mix(in srgb, var(--sp-success) 25%, var(--sp-border)); border-radius: var(--sp-radius-pill); }
.catalog-panel { min-height: 540px; padding: 18px; background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(22px); }
.catalog-tabs :deep(.el-tabs__header.is-left) { width: 194px; margin-right: 20px; padding: 7px; background: var(--sp-gradient-night); border: 1px solid rgb(255 255 255 / 9%); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-md); }
.catalog-tabs :deep(.el-tabs__item) { min-height: 46px; gap: 8px; margin: 3px; padding: 0 13px !important; color: var(--sp-on-night-muted); font-weight: 650; border-radius: var(--sp-radius-sm); }.catalog-tabs :deep(.el-tabs__item:hover) { color: var(--sp-on-night); background: rgb(255 255 255 / 8%); }.catalog-tabs :deep(.el-tabs__item.is-active) { color: var(--sp-night); background: var(--sp-energy-lime); box-shadow: 0 9px 22px rgb(0 0 0 / 20%); }.catalog-tabs :deep(.el-tabs__active-bar), .catalog-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }.catalog-tabs :deep(.el-tabs__content) { min-height: 500px; overflow: visible; }
.card-container { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; padding: 2px; }
.catalog-card { overflow: hidden; cursor: pointer; transition: transform var(--sp-duration-base) var(--sp-ease-standard), box-shadow var(--sp-duration-base) ease, border-color var(--sp-duration-base) ease; }.catalog-card:hover { transform: translateY(-3px); border-color: color-mix(in srgb, var(--sp-brand) 35%, var(--sp-border)); box-shadow: var(--sp-shadow-md); }.catalog-card :deep(.el-card__body) { padding: 0; }.catalog-card .catalog-image { width: 100%; height: 180px; display: block; background: var(--sp-surface-muted); }
.card-info { display: grid; gap: 8px; padding: 14px 15px 16px; }.card-info strong { overflow: hidden; color: var(--sp-text); font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }.card-info span { display: flex; justify-content: space-between; color: var(--sp-text-muted); font-size: 11px; }.card-info b { color: var(--sp-brand); font-size: 15px; }
@media (max-width: 1050px) { .card-container { grid-template-columns: 1fr 1fr; } }
@media (max-width: 760px) { .catalog-page { width: calc(100% - 24px); padding-top: 22px; }.catalog-heading { align-items: flex-start; flex-direction: column; }.catalog-panel { min-height: 0; padding: 10px; }.catalog-tabs :deep(.el-tabs__header.is-top) { margin: 0 0 14px; padding: 6px; background: var(--sp-gradient-night); border-radius: var(--sp-radius-md); }.catalog-tabs :deep(.el-tabs__nav-scroll) { overflow-x: auto; }.catalog-tabs :deep(.el-tabs__nav) { min-width: max-content; }.catalog-tabs :deep(.el-tabs__content) { min-height: 0; }.card-container { grid-template-columns: 1fr; }.catalog-card .catalog-image { height: 210px; } }
</style>
