<template>
  <FrontHeaderMenu />
  <div class="ai-entry">
    <section class="entry-card" aria-labelledby="ai-title">
      <img class="assistant-portrait" :src="companionPortrait" alt="小伴生活服务助理" />
      <h1 id="ai-title">小伴生活服务助理</h1>
      <p>用日常说法交代活动、上门服务、健康报告或膳食需求。涉及报名、预约和取消时，会在执行前请你确认。</p>
      <el-alert
        v-if="!isLoggedIn"
        title="使用生活助理前请先登录系统"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-button type="primary" size="large" round @click="enterAssistant">
        {{ isLoggedIn ? '请小伴帮忙' : '前往登录' }}
      </el-button>
      <span class="security-note">业务写操作需要当前用户再次确认</span>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import FrontHeaderMenu from '@/components/front/home/FrontHeaderMenu.vue'
import companionPortrait from '@/assets/assistant/xiaoban-care-coordinator.webp'

const router = useRouter()
const isLoggedIn = computed(() => Boolean(sessionStorage.getItem('token') || localStorage.getItem('token')))

onMounted(() => {
  // 清理旧版本曾错误保存在浏览器中的第三方 API 密钥。
  localStorage.removeItem('deepseek_api_key')
  sessionStorage.removeItem('apiKey')
})

const enterAssistant = () => {
  router.push(isLoggedIn.value ? '/front/ai/AiChat' : '/login')
}
</script>

<style scoped>
.ai-entry {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: calc(var(--sp-header-height) + 34px) 20px 38px;
  background: transparent;
}

.entry-card {
  position: relative;
  isolation: isolate;
  width: min(920px, 100%);
  min-height: 540px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  overflow: hidden;
  padding: 62px 52px;
  text-align: center;
  color: var(--sp-on-night);
  background: var(--sp-gradient-aurora);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: var(--sp-radius-xl);
  box-shadow: var(--sp-shadow-lg);
}
.entry-card::before { content: ''; position: absolute; z-index: -1; width: 430px; aspect-ratio: 1; top: -260px; right: -90px; border: 1px solid rgb(255 255 255 / 14%); border-radius: 50%; box-shadow: 0 0 0 62px rgb(255 255 255 / 3%), 0 0 0 124px rgb(255 255 255 / 2%); animation: sp-ambient-drift 14s var(--sp-ease-standard) infinite alternate; }

.assistant-portrait { width: 132px; height: 132px; object-fit: cover; object-position: center 12%; background: var(--sp-brand-soft); border: 4px solid rgb(217 255 116 / 72%); border-radius: 50%; box-shadow: 0 0 0 10px rgb(217 255 116 / 6%), 0 24px 54px rgb(0 0 0 / 28%); animation: assistant-breathe 4.8s ease-in-out infinite; }

h1 {
  margin: 0;
  color: var(--sp-on-night);
  font-size: clamp(38px, 5vw, 58px);
  line-height: 1.05;
  letter-spacing: -.05em;
}

p {
  max-width: 680px;
  margin: 0;
  color: var(--sp-on-night-muted);
  font-size: 15px;
  line-height: 1.75;
}

.entry-card :deep(.el-alert) { width: min(620px, 100%); background: rgb(255 255 255 / 8%); border-color: rgb(255 255 255 / 13%); backdrop-filter: blur(12px); }
.entry-card :deep(.el-alert__title) { color: var(--sp-on-night); }

.entry-card :deep(.el-button) {
  width: min(420px, 100%);
  min-height: 50px;
}

.security-note {
  color: var(--sp-on-night-muted);
  font-size: 12px;
}

@keyframes assistant-breathe { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-3px); } }
@media (max-width: 640px) { .entry-card { min-height: 580px; padding: 44px 24px; border-radius: var(--sp-radius-lg); }.assistant-portrait { width: 112px; height: 112px; } }
@media (prefers-reduced-motion: reduce) { .assistant-portrait { animation: none; } }
</style>
