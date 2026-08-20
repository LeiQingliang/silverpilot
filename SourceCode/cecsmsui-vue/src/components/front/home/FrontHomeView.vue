<template>
  <div class="home-container">
    <section class="product-hero" aria-labelledby="assistant-entry-title">
      <div
        class="hero-base hero-zoom"
        :style="{ backgroundImage: `url(${heroBaseImage})` }"
        aria-hidden="true"
      ></div>
      <div class="hero-base-shade" aria-hidden="true"></div>
      <SpotlightReveal
        :image="heroRevealImage"
        :cursor-x="cursorPos.x"
        :cursor-y="cursorPos.y"
        :radius="SPOTLIGHT_R"
      />
      <div class="hero-vignette" aria-hidden="true"></div>

      <div class="hero-heading">
        <span
          class="hero-heading-line hero-heading-line-one font-playfair hero-anim hero-reveal"
          style="animation-delay: 0.25s"
        >照护有温度</span>
        <span
          id="assistant-entry-title"
          class="hero-heading-line hero-heading-line-two hero-anim hero-reveal"
          style="animation-delay: 0.42s"
        >安心有回应</span>
      </div>

      <div class="hero-story hero-anim hero-fade" style="animation-delay: 0.7s">
        <p>SilverPilot 把活动、上门服务、健康档案与助餐预订收在一个入口，让长者和照护者更容易找到真正需要的服务。</p>
      </div>

      <div class="hero-action hero-anim hero-fade" style="animation-delay: 0.85s">
        <p>小伴先查询真实业务数据，再给出清楚建议；报名、预约与取消都会说明影响，并由你再次确认。</p>
        <button type="button" class="hero-cta" @click="goAgent">
          <span>请小伴帮忙</span>
          <ArrowRight aria-hidden="true" />
        </button>
      </div>
    </section>

    <section class="service-principles" aria-label="平台服务原则">
      <article><strong>一个入口</strong><span>活动、服务、健康和膳食统一查找</span></article>
      <article><strong>清楚确认</strong><span>提交与取消前展示对象、时间和影响</span></article>
      <article><strong>适老设计</strong><span>清晰文字、足够触控区和错误恢复</span></article>
    </section>

    <CareLandscape
      @open-agent="goAgent"
      @open-services="goMore('communityService')"
    />

    <header class="home-section-heading">
      <div><span>本周精选</span><h2>社区里的新鲜事</h2></div>
      <p>来自系统当前可用的活动与服务数据。</p>
    </header>

    <!-- 轮播图区 -->
    <el-skeleton v-if="loading" class="home-skeleton" :rows="5" animated />
    <el-carousel v-else-if="tableData.length" class="home-carousel" height="clamp(260px, 28vw, 360px)" :interval="6000" arrow="always" pause-on-hover>
      <el-carousel-item v-for="(item, index) in tableData" :key="item.id || index">
        <article class="carousel-item">
          <SmartImage
            :src="item.image"
            :alt="item.activityName"
            fit="cover"
            :priority="index === 0"
            fallback-label="养老活动"
            class="carousel-img"
          />
          <div class="carousel-mask">
            <span>本期推荐活动</span>
            <div class="carousel-title">{{ item.activityName }}</div>
            <div class="carousel-meta">
              <small>{{ item.activityDate || '日期待公布' }}</small>
              <button type="button" @click="toDetail(item.id, item.image)">查看活动详情</button>
            </div>
          </div>
        </article>
      </el-carousel-item>
    </el-carousel>
    <el-empty v-else class="home-empty" description="暂时没有可展示的活动">
      <el-button v-if="loadError" type="primary" plain @click="loadContent">重新加载</el-button>
    </el-empty>

    <!-- 内容模块 -->
    <div class="content-wrapper">
      <!-- 老年活动 -->
      <el-card shadow="never" class="feature-card" aria-label="养老活动推荐">
        <template #header>
          <div class="card-header">
            <div class="header-title">
              <el-icon class="header-icon"><Calendar /></el-icon>
              <span>老年活动</span>
            </div>
            <el-button link type="primary" class="more-btn" @click="goMore('communityActivity')">
              更多 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </template>
        <el-skeleton v-if="loading" :rows="2" animated />
        <div v-else-if="allActivities.length" :class="['scroll-list', { sparse: allActivities.length <= 2 }]">
          <button class="scroll-item" type="button" v-for="item in allActivities" :key="item.id" :aria-label="`查看活动：${item.activityName}`" @click="goActivityDetail(item.id)">
            <SmartImage :src="item.image" :alt="item.activityName" fit="cover" fallback-label="活动" class="item-img" />
            <span class="item-copy"><span class="item-name">{{ item.activityName }}</span><small>{{ item.activityDate || '日期待公布' }} · 查看详情</small></span>
          </button>
        </div>
        <el-empty v-else description="暂无可报名活动" :image-size="72" />
      </el-card>

      <!-- 老年服务 -->
      <el-card shadow="never" class="feature-card" aria-label="养老服务推荐">
        <template #header>
          <div class="card-header">
            <div class="header-title">
              <el-icon class="header-icon"><FirstAidKit /></el-icon>
              <span>老年服务</span>
            </div>
            <el-button link type="primary" class="more-btn" @click="goMore('communityService')">
              更多 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </template>
        <el-skeleton v-if="loading" :rows="2" animated />
        <div v-else-if="allServices.length" class="scroll-list">
          <button class="scroll-item" type="button" v-for="item in allServices" :key="item.id" :aria-label="`预约服务：${item.serviceName}`" @click="goServiceDetail(item)">
            <SmartImage :src="item.image" :alt="item.serviceName" fit="cover" fallback-label="服务" class="item-img" />
            <span class="item-copy"><span class="item-name">{{ item.serviceName }}</span><small>查看服务与预约信息</small></span>
          </button>
        </div>
        <el-empty v-else description="暂无在用养老服务" :image-size="72" />
      </el-card>
    </div>

    <!-- 预约弹窗 -->
    <FrontServiceDialog />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from "pinia"
import { useActivityStore } from "../../../stores/activity.js"
import { useServicetypeStore } from "../../../stores/servicetype.js"
import $axios from '../../../utils/axios'
import { ElMessage } from 'element-plus'
import { Calendar, FirstAidKit } from '@element-plus/icons-vue'
import { ArrowRight } from '@lucide/vue'
import FrontServiceDialog from '../../../components/front/service/FrontServiceDialog.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import CareLandscape from './CareLandscape.vue'
import SpotlightReveal from './SpotlightReveal.vue'
import heroBaseImage from '@/assets/img/login5.webp'
import heroRevealImage from '@/assets/img/activity1.png'

const SPOTLIGHT_R = 260
const mouse = ref({ x: -999, y: -999 })
const smooth = ref({ x: -999, y: -999 })
const cursorPos = ref({ x: -999, y: -999 })
const rafRef = ref()

const activityStore = useActivityStore()
const { tableData } = storeToRefs(activityStore)
const servicetypeStore = useServicetypeStore()
const router = useRouter()
const loading = ref(true)
const loadError = ref('')

// 所有社区活动（完整列表）
const allActivities = computed(() => {
  return tableData.value || []
})

// 所有社区服务（完整列表，包含父分类信息）
const allServices = ref([])

// 获取所有服务（附加父分类）
const fetchAllServices = async () => {
  try {
    await servicetypeStore.selectFather()
    const fatherTypes = servicetypeStore.fatherType
    if (!fatherTypes.length) return

    const promises = fatherTypes.map(type =>
      $axios.get(`/serviceType/selectAllChildrenByFather/${type.id}`)
    )
    const responses = await Promise.all(promises)

    let services = []
    responses.forEach((res, idx) => {
      if (res.data.code === 200 && res.data.result) {
        const father = fatherTypes[idx]
        const children = res.data.result.map(child => ({
          ...child,
          fatherId: father.id,
          fatherName: father.serviceName
        }))
        services = services.concat(children)
      }
    })

    // 去重（假设id唯一）
    const uniqueServices = services.filter((s, index, self) =>
      index === self.findIndex(t => t.id === s.id)
    )
    allServices.value = uniqueServices
  } catch (error) {
    console.error('获取服务列表失败', error)
    throw error
  }
}

const loadContent = async () => {
  loading.value = true
  loadError.value = ''
  try {
    await Promise.all([activityStore.initNotBegin(), fetchAllServices()])
  } catch {
    loadError.value = '首页数据加载失败'
    ElMessage.error('首页数据暂时无法加载，请稍后重试')
  } finally {
    loading.value = false
  }
}

const trackMouse = (event) => {
  mouse.value.x = event.clientX
  mouse.value.y = event.clientY
}

const animateSpotlight = () => {
  smooth.value.x += (mouse.value.x - smooth.value.x) * 0.1
  smooth.value.y += (mouse.value.y - smooth.value.y) * 0.1
  cursorPos.value = { x: smooth.value.x, y: smooth.value.y }
  rafRef.value = window.requestAnimationFrame(animateSpotlight)
}

onMounted(() => {
  loadContent()
  window.addEventListener('mousemove', trackMouse)
  rafRef.value = window.requestAnimationFrame(animateSpotlight)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', trackMouse)
  if (rafRef.value) window.cancelAnimationFrame(rafRef.value)
})

// 活动详情跳转
const toDetail = (id, image) => {
  activityStore.activityDetail.id = id
  activityStore.activityDetail.image = image
  router.push({ path: '/front/activity/ActivityDetailView', query: { Id: id } })
}

// 更多按钮
const goMore = (type) => {
  if (type === 'communityActivity') {
    router.push('/front/activity/FrontActivityView')
  } else if (type === 'communityService') {
    router.push('/front/service/FrontServiceView')
  }
}

const goAgent = () => router.push('/front/ai/AiChat')

const goActivityDetail = (id) => {
  router.push({ path: '/front/activity/ActivityDetailView', query: { Id: id } })
}

// 服务点击：直接打开预约弹窗
const goServiceDetail = (service) => {
  const parent = { id: service.fatherId, serviceName: service.fatherName }
  const child = { id: service.id, serviceName: service.serviceName, image: service.image }
  servicetypeStore.pre4Order(parent, child)
}
</script>

<style scoped lang="scss">
.home-container {
  min-height: 100vh;
  background: var(--sp-bg);
  padding-bottom: 64px;
}
.home-skeleton, .home-empty { width: min(var(--sp-content-max), calc(100% - 40px)); min-height: 300px; margin: 24px auto 32px; padding: 28px; background: var(--sp-surface); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-lg); box-sizing: border-box; }
.image-fallback { width: 100%; height: 100%; display: grid; place-items: center; color: var(--sp-text-muted); font-weight: 650; background: linear-gradient(135deg, var(--sp-brand-soft), var(--sp-surface-muted)); }
.image-fallback.compact { font-size: 12px; }

/* 轮播图区 */
.home-carousel {
  width: min(var(--sp-content-max), calc(100% - 40px));
  max-width: 1280px;
  margin: 24px auto 32px;
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-lg);
  overflow: hidden;
  box-shadow: var(--sp-shadow-md);
  background: var(--sp-surface-muted);

  .carousel-item {
    position: relative;
    width: 100%;
    height: 100%;
    cursor: pointer;
    overflow: hidden;
    padding: 0;
    text-align: left;
    background: transparent;
    border: 0;

    .carousel-img {
      width: 100%;
      height: 100%;
      transition: transform var(--sp-duration-slow) var(--sp-ease-standard);
    }

    &:hover .carousel-img {
      transform: scale(1.02);
    }

    .carousel-mask {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      background: linear-gradient(to top, rgba(5,14,28,.86), rgba(5,14,28,.06));
      padding: 42px 26px 22px;
      text-align: left;

      .carousel-title {
        font-size: 21px;
        font-weight: 740;
        color: white;
        text-shadow: 0 1px 2px rgba(0,0,0,.2);
      }
      > span { color: #dbeee6; font-size: 10px; font-weight: 750; letter-spacing: .05em; }
      .carousel-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 5px; }
      .carousel-meta small { color: #d8e2ed; font-size: 12px; }
      .carousel-meta button { min-height: 36px; padding: 0 13px; color: #fff; font-size: 13px; font-weight: 700; background: rgba(255,255,255,.14); border: 1px solid rgba(255,255,255,.5); border-radius: var(--sp-radius-xs); cursor: pointer; }
      .carousel-meta button:hover { background: rgba(255,255,255,.24); }
    }
  }
}

/* 内容区域 */
.content-wrapper {
  width: min(var(--sp-content-max), calc(100% - 40px));
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

/* 卡片样式 */
.feature-card {
  min-width: 0;
  border-radius: var(--sp-radius-lg);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  box-shadow: var(--sp-shadow-sm);
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 16px 32px -12px rgba(0, 0, 0, 0.12);
  }

  :deep(.el-card__header) {
    border-bottom: 1px solid var(--sp-border);
    padding: 20px 24px;
  }

  :deep(.el-card__body) {
    padding: 20px 24px 24px;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 18px;
    font-weight: 600;
    color: var(--sp-text);

    .header-icon {
      color: var(--sp-brand);
      font-size: 20px;
    }
  }

  .more-btn {
    font-size: 14px;
    font-weight: 500;
    color: var(--sp-brand);
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
    border-radius: 40px;
    transition: color var(--sp-duration-fast) ease, background-color var(--sp-duration-fast) ease;

    &:hover {
      background: var(--sp-brand-soft);
      color: var(--sp-brand-strong);
    }

    .el-icon {
      font-size: 14px;
    }
  }
}

/* 横向滚动列表 */
.scroll-list {
  display: flex;
  overflow-x: auto;
  gap: 20px;
  padding: 8px 0 12px;
  scrollbar-width: thin;
  scrollbar-color: var(--sp-border-strong) var(--sp-surface-muted);

  &::-webkit-scrollbar {
    height: 6px;
  }
  &::-webkit-scrollbar-track {
    background: var(--sp-surface-muted);
    border-radius: 10px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--sp-border-strong);
    border-radius: 10px;
    &:hover {
      background: var(--sp-text-muted);
    }
  }
}

.scroll-item {
  flex-shrink: 0;
  width: 148px;
  cursor: pointer;
  text-align: center;
  padding: 0;
  color: inherit;
  background: var(--sp-surface-muted);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-md);
  overflow: hidden;
  transition: transform var(--sp-duration-fast) ease, border-color var(--sp-duration-fast) ease;

  &:hover {
    transform: translateY(-4px);

    border-color: color-mix(in srgb, var(--sp-brand) 34%, var(--sp-border));
    .item-name {
      color: var(--sp-brand);
    }
  }

  .item-img {
    width: 100%;
    height: 116px;
    border-radius: 0;
    object-fit: cover;
    background: var(--sp-surface-muted);
    transition: transform var(--sp-duration-base) ease;
  }

  .item-copy { display: grid; gap: 5px; padding: 11px 12px 13px; text-align: left; }

  .item-name {
    display: block;
    font-size: 14px;
    font-weight: 500;
    color: var(--sp-text-secondary);
    margin: 0;
    line-height: 1.4;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color 0.2s;
  }

  .item-copy small { color: var(--sp-text-muted); font-size: 10px; line-height: 1.4; }
}

.scroll-list.sparse .scroll-item {
  width: min(100%, 420px);
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  align-items: center;
  text-align: left;
}
.scroll-list.sparse .item-img { height: 128px; }
.scroll-list.sparse .item-copy { padding: 18px; }
.scroll-list.sparse .item-name { font-size: 16px; font-weight: 650; white-space: normal; }

/* 响应式适配 */
@media (max-width: 1024px) {
  .home-carousel, .content-wrapper, .home-skeleton, .home-empty {
    width: 90%;
  }
  .scroll-item {
    width: 140px;
    .item-img {
      width: 100%;
      height: 110px;
    }
  }
}

@media (max-width: 700px) {
  .content-wrapper { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .home-carousel {
    width: 95%;
    margin-top: 16px;
    border-radius: 20px;
    .carousel-mask .carousel-title {
      font-size: 16px;
    }
  }
  .content-wrapper {
    width: 95%;
    gap: 24px;
  }
  .feature-card :deep(.el-card__header) {
    padding: 16px 20px;
  }
  .feature-card :deep(.el-card__body) {
    padding: 16px 20px 20px;
  }
  .card-header .header-title {
    font-size: 16px;
  }
  .scroll-item {
    width: 120px;
    .item-img {
      width: 100%;
      height: 96px;
      border-radius: 0;
    }
  }
  .scroll-list.sparse .scroll-item { width: 100%; grid-template-columns: 120px minmax(0, 1fr); }
  .scroll-list.sparse .item-img { height: 108px; }
  .scroll-list.sparse .item-copy { padding: 14px; }
}

/* Immersive homepage stage */
.product-hero {
  position: relative;
  isolation: isolate;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-height: 620px;
  overflow: hidden;
  color: #fff;
  background: #050606;
  letter-spacing: -.02em;
}

.hero-base {
  position: absolute;
  z-index: 10;
  inset: 0;
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  filter: brightness(.46) saturate(.72) contrast(1.05);
}

.hero-base-shade {
  position: absolute;
  z-index: 20;
  inset: 0;
  background: rgb(3 9 14 / 28%);
  pointer-events: none;
}

.hero-vignette {
  position: absolute;
  z-index: 40;
  inset: 0;
  background:
    linear-gradient(180deg, rgb(2 7 12 / 58%) 0%, transparent 26%, transparent 60%, rgb(2 7 12 / 72%) 100%),
    linear-gradient(90deg, rgb(2 7 12 / 30%) 0%, transparent 28%, transparent 70%, rgb(2 7 12 / 26%) 100%);
  pointer-events: none;
}

.hero-heading {
  position: absolute;
  z-index: 50;
  top: 14%;
  right: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 20px;
  text-align: center;
  pointer-events: none;
}

.hero-heading-line {
  display: block;
  color: #fff;
  font-size: 48px;
  font-weight: 400;
  line-height: .95;
  text-shadow: 0 3px 32px rgb(0 0 0 / 32%);
}

.hero-heading-line-one {
  font-style: italic;
  letter-spacing: -.05em;
}

.hero-heading-line-two {
  margin-top: -4px;
  letter-spacing: -.08em;
}

.hero-story,
.hero-action {
  position: absolute;
  z-index: 50;
}

.hero-story {
  bottom: 56px;
  left: 40px;
  max-width: 280px;
}

.hero-story p,
.hero-action p {
  margin: 0;
  color: rgb(255 255 255 / 82%);
  line-height: 1.75;
}

.hero-story p { font-size: 14px; }

.hero-action {
  right: 40px;
  bottom: 96px;
  max-width: 280px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 20px;
}

.hero-action p { font-size: 14px; }

.hero-cta {
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 26px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  background: #b84b18;
  border: 0;
  border-radius: 999px;
  box-shadow: 0 12px 32px rgb(184 75 24 / 24%);
  cursor: pointer;
  transition: transform .24s var(--sp-ease-expressive), background-color .2s ease, box-shadow .24s ease;
}

.hero-cta svg { width: 17px; height: 17px; transition: transform .24s var(--sp-ease-expressive); }
.hero-cta:hover { background: #9e3e10; box-shadow: 0 16px 38px rgb(184 75 24 / 34%); transform: scale(1.03); }
.hero-cta:hover svg { transform: translateX(2px); }
.hero-cta:active { transform: scale(.95); }
.hero-cta:focus-visible { outline: 3px solid rgb(255 255 255 / 92%); outline-offset: 4px; }

.service-principles { width: min(var(--sp-content-max), calc(100% - 40px)); display: grid; grid-template-columns: repeat(3, 1fr); gap: 1px; margin: 18px auto 0; overflow: hidden; background: #d9d9de; border: 1px solid #d9d9de; border-radius: var(--sp-radius-lg); }
.service-principles article { min-height: 116px; display: grid; align-content: center; gap: 7px; padding: 24px 28px; background: #fff; }
.service-principles strong { color: var(--sp-text); font-size: 17px; font-weight: 650; letter-spacing: -.02em; }
.service-principles span { color: var(--sp-text-muted); font-size: 12px; line-height: 1.55; }
.home-section-heading { width: min(var(--sp-content-max), calc(100% - 40px)); display: flex; align-items: end; justify-content: space-between; gap: 24px; margin: 76px auto 22px; padding: 0 4px; }
.home-section-heading span { color: var(--sp-brand); font-size: 13px; font-weight: 650; }
.home-section-heading h2 { margin: 5px 0 0; font-size: clamp(34px, 4vw, 52px); line-height: 1.05; letter-spacing: -.055em; }
.home-section-heading p { max-width: 360px; margin: 0; color: var(--sp-text-muted); font-size: 13px; text-align: right; }
.home-carousel { margin-top: 0; border-color: #e5e5ea; box-shadow: none; }
.home-carousel :deep(.el-carousel__container) { height: clamp(320px, 34vw, 450px) !important; }
.home-carousel .carousel-item .carousel-mask { padding: 76px 34px 28px; background: linear-gradient(to top, rgb(0 0 0 / 76%), transparent); }
.content-wrapper { gap: 18px; margin-top: 18px; }
.feature-card:hover { transform: none; box-shadow: none; }
.scroll-item { background: #fff; border-color: #e5e5ea; }

@media (min-width: 640px) {
  .hero-heading-line { font-size: 72px; }
}

@media (min-width: 768px) {
  .hero-heading-line { font-size: 96px; }
  .hero-story { left: 56px; }
  .hero-action { right: 56px; }
}

@media (max-width: 820px) {
  .service-principles { width: calc(100% - 24px); grid-template-columns: 1fr; }
  .service-principles article { min-height: 92px; }
  .home-section-heading { width: calc(100% - 24px); margin-top: 58px; }
}

@media (max-width: 639px) {
  .product-hero { min-height: 600px; }
  .hero-heading { top: 15%; }
  .hero-heading-line { font-size: clamp(46px, 14vw, 58px); }
  .hero-story { display: none; }
  .hero-action {
    right: 20px;
    bottom: 28px;
    left: 20px;
    max-width: none;
    gap: 16px;
  }
  .hero-action p { font-size: 12px; line-height: 1.7; }
  .hero-cta { min-height: 48px; padding: 0 24px; }
  .home-section-heading { align-items: flex-start; flex-direction: column; }
  .home-section-heading p { text-align: left; }
  .home-carousel, .content-wrapper, .home-skeleton, .home-empty { width: calc(100% - 24px); }
  .home-carousel :deep(.el-carousel__container) { height: 300px !important; }
}

@media (max-height: 700px) and (min-width: 640px) {
  .hero-heading { top: 13%; }
  .hero-heading-line { font-size: 64px; }
  .hero-story { bottom: 30px; }
  .hero-action { bottom: 30px; gap: 14px; }
}
</style>
