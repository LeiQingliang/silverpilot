<template>
  <div class="login-page">
    <FrontHeaderMenu />
    <div class="login-container">
      <div class="background-overlay"></div>
      <div class="login-shell">
        <section class="brand-panel" aria-labelledby="brand-title">
          <div class="brand-mark"><span>S</span> SilverPilot 智慧养老</div>
          <div class="brand-intro">
            <p class="brand-kicker">一个入口，连接社区照护</p>
            <h2 id="brand-title">让每一次服务，<br><em>都简单、清楚。</em></h2>
            <p class="brand-copy">活动、上门服务、健康档案和生活助理集中在这里。关键操作会在执行前再次确认。</p>
          </div>
          <div class="brand-visual">
            <SmartImage :src="loginIllustration" alt="社区长者在明亮活动空间交流、下棋和园艺" fit="cover" priority fallback-label="智慧养老社区" />
            <div><small>为长者和照护者设计</small><strong>看得清 · 点得准 · 找得到</strong></div>
          </div>
          <div class="capability-list" aria-label="平台服务能力">
            <span>社区活动</span><span>上门服务</span><span>健康档案</span><span>生活助理</span>
          </div>
          <small>演示系统 · 智能建议不替代医疗诊断或专业照护评估</small>
        </section>
        <div class="login-card">
        <el-card class="card" shadow="never">
          <template #header>
            <div class="card-header">
              <h1 class="title">欢迎回来</h1>
              <p class="subtitle">智慧养老服务系统</p>
            </div>
          </template>
          <el-form
            :model="ruleForm"
            ref="ruleFormRef"
            :size="formSize"
            status-icon
            :rules="rules"
            class="login-form"
            @submit.prevent="submitForm(ruleFormRef)"
          >
            <el-form-item prop="loginName">
              <el-input
                v-model="ruleForm.loginName"
                placeholder="用户名 / 手机号"
                :prefix-icon="Avatar"
                class="custom-input"
                autocomplete="username"
                aria-label="用户名或手机号"
                clearable
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="ruleForm.password"
                placeholder="密码"
                :prefix-icon="Lock"
                type="password"
                show-password
                class="custom-input"
                autocomplete="current-password"
                aria-label="密码"
                clearable
              />
            </el-form-item>
            <el-form-item prop="code">
              <el-input
                v-model.trim="ruleForm.code"
                placeholder="验证码"
                :prefix-icon="Bell"
                autocomplete="off"
                class="custom-input"
                aria-label="验证码"
                clearable
              />
            </el-form-item>
            <div class="captcha-wrapper">
              <button class="captcha-button" type="button" aria-label="刷新验证码" @click="resetImg">
                <span class="captcha-visual" :aria-busy="captchaLoading">
                  <el-image
                    v-show="!captchaError"
                    :src="imgUrl"
                    class="captcha-img"
                    alt="验证码图片"
                    @load="handleCaptchaLoad"
                    @error="handleCaptchaError"
                  />
                  <span v-if="captchaLoading && !captchaError" class="captcha-status">加载中…</span>
                  <span v-if="captchaError" class="captcha-status error">加载失败，点击重试</span>
                </span>
                <span class="captcha-tip">看不清？刷新验证码</span>
              </button>
            </div>
            <el-form-item>
              <div class="action-buttons">
                <el-button type="primary" native-type="submit" :loading="submitting" :icon="Promotion" class="login-btn">
                  登录
                </el-button>
                <el-button link type="primary" @click="register" class="register-link">
                  还没有账号？立即注册
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import FrontHeaderMenu from '@/components/front/home/FrontHeaderMenu.vue'
import SmartImage from '@/components/common/SmartImage.vue'
import loginIllustration from '@/assets/img/login5.webp'
import { Avatar, Lock, Promotion, Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import $axios, { apiUrl } from '../utils/axios'

const createCaptchaKey = () => globalThis.crypto?.randomUUID?.()
  || `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

let key = createCaptchaKey()
const imgUrl = ref(apiUrl('/user/getVerificationCode/' + key))
const captchaLoading = ref(true)
const captchaError = ref(false)

const resetImg = () => {
  key = createCaptchaKey()
  ruleForm.value.code = ''
  captchaLoading.value = true
  captchaError.value = false
  imgUrl.value = apiUrl('/user/getVerificationCode/' + key)
}
const handleCaptchaLoad = () => {
  captchaLoading.value = false
  captchaError.value = false
}
const handleCaptchaError = () => {
  captchaLoading.value = false
  captchaError.value = true
}

const router = useRouter()
const formSize = ref('default')
const ruleFormRef = ref()
const ruleForm = ref({ loginName: '', password: '', code: '' })
const submitting = ref(false)

const rules = reactive({
  loginName: [
    { required: true, message: '请输入用户名或手机号', trigger: 'blur' },
    { min: 5, max: 11, message: '5-11个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 18, message: '6-18个字符', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
})

const submitForm = async (formEl) => {
  if (!formEl || submitting.value) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  const path = '/user/login/' + key

  try {
    const { data: res } = await $axios.post(path, ruleForm.value, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      suppressErrorToast: true
    })

    if (res.code === 200) {
      const user = res.result
      const sessionUser = {
        id: user.id,
        roleId: user.roleId,
        username: user.username,
        name: user.name
      }
      sessionStorage.clear()
      for (const authKey of ['token', 'user', 'id', 'roleId']) localStorage.removeItem(authKey)
      sessionStorage.setItem('user', JSON.stringify(sessionUser))
      sessionStorage.setItem('id', user.id + '')
      sessionStorage.setItem('token', user.token)
      sessionStorage.setItem('roleId', user.roleId + '')
      sessionStorage.setItem('isNotified', 'false')
      const landingRoutes = {
        1: '/IndexView', 2: '/ActivityMenageView', 3: '/HealthOrderView', 4: '/front/home/FrontHomeView'
      }
      await router.push(landingRoutes[user.roleId] || '/front/home/FrontHomeView')
    } else {
      const messages = { 100: '用户名或密码错误', 108: '验证码错误或已过期', 429: '登录尝试过于频繁，请稍后重试' }
      ElMessage.error(messages[res.code] || res.msg || '登录失败')
      resetImg()
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || (error.code === 'ECONNABORTED' ? '登录请求超时' : '登录请求失败，请检查服务状态'))
    resetImg()
  } finally {
    submitting.value = false
  }
}

const register = () => {
  router.push('/register')
}
</script>

<style lang="scss" scoped>
:global(body) {
  margin: 0 !important;
  padding-top: 0 !important;
}

.login-container {
  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 104px 24px 42px;
  background: transparent;
  box-sizing: border-box;
}

.background-overlay { display: none; }

.login-shell {
  position: relative;
  z-index: 2;
  width: min(1140px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(390px, .8fr);
  overflow: hidden;
  background: var(--sp-surface);
  border: 1px solid rgb(255 255 255 / 72%);
  border-radius: var(--sp-radius-xl);
  box-shadow: var(--sp-shadow-lg);
}

.brand-panel {
  position: relative;
  isolation: isolate;
  min-height: 610px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 52px;
  color: var(--sp-on-night);
  background: var(--sp-gradient-aurora);
}
.brand-panel::before { content: ''; position: absolute; z-index: -1; width: 430px; aspect-ratio: 1; top: -210px; right: -130px; border: 1px solid rgb(255 255 255 / 14%); border-radius: 50%; box-shadow: 0 0 0 60px rgb(255 255 255 / 3%), 0 0 0 120px rgb(255 255 255 / 2%); animation: sp-ambient-drift 14s var(--sp-ease-standard) infinite alternate; }
.brand-mark { display: flex; align-items: center; gap: 11px; font-size: 12px; font-weight: 750; letter-spacing: .06em; }
.brand-mark span { width: 36px; height: 36px; display: grid; place-items: center; color: var(--sp-night); font-size: 14px; background: var(--sp-energy-lime); border-radius: 50%; box-shadow: 0 0 28px rgb(217 255 116 / 22%); }
.brand-kicker { margin: 0 0 12px; color: var(--sp-on-night-muted); font-size: 12px; font-weight: 700; letter-spacing: .07em; }
.brand-panel h2 { margin: 0; font-size: clamp(40px, 4.7vw, 64px); line-height: 1.04; letter-spacing: -.055em; }
.brand-panel h2 em { color: var(--sp-on-night-muted); font-style: normal; }
.brand-copy { max-width: 560px; margin: 22px 0 0; color: var(--sp-on-night-muted); font-size: 14px; line-height: 1.85; }
.capability-list { display: flex; flex-wrap: wrap; gap: 8px; }
.capability-list span { padding: 7px 11px; color: var(--sp-on-night-muted); font-size: 11px; background: rgb(255 255 255 / 7%); border: 1px solid rgb(255 255 255 / 12%); border-radius: 999px; backdrop-filter: blur(10px); }
.brand-panel > small { color: rgb(203 214 209 / 68%); font-size: 10px; line-height: 1.5; }

.login-card {
  display: grid;
  align-items: center;
  padding: 40px 32px;
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(24px);
  animation: fadeInUp 0.6s cubic-bezier(0.2, 0.9, 0.4, 1.1);
}

.card {
  background: transparent;
  border-radius: 24px;
  border: none;
  box-shadow: none;

  :deep(.el-card__header) {
    border-bottom: none;
    padding: 28px 32px 0 32px;
  }

  .card-header {
    text-align: center;
    .title {
      font-size: 34px;
      font-weight: 600;
      color: var(--sp-text);
      margin: 0 0 8px 0;
      letter-spacing: -0.5px;
    }
    .subtitle {
      font-size: 14px;
      color: var(--sp-text-muted);
      margin: 0;
      font-weight: 400;
    }
  }

  .login-form {
    padding: 24px 32px 32px;

    .custom-input {
      :deep(.el-input__wrapper) {
        border-radius: var(--sp-radius-sm);
        background-color: var(--sp-surface-muted);
        box-shadow: none;
        border: 1px solid var(--sp-border);
        transition: all 0.2s;
        padding: 4px 16px;
      }

      :deep(.el-input__wrapper:hover) {
        border-color: color-mix(in srgb, var(--sp-brand) 42%, var(--sp-border));
      }

      :deep(.el-input__wrapper.is-focus) {
        border-color: var(--sp-brand);
        box-shadow: var(--sp-focus);
      }

      :deep(.el-input__inner) {
        font-size: 15px;
        padding: 12px 0;
      }

      :deep(.el-input__prefix) {
        font-size: 18px;
        color: var(--sp-text-muted);
      }
    }

    .captcha-wrapper {
      display: flex;
      align-items: center;
      margin-bottom: 28px;
    }

    .captcha-button {
      width: 100%;
      min-height: 54px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      padding: 3px 12px 3px 3px;
      color: var(--sp-text-muted);
      background: var(--sp-surface-muted);
      border: 1px solid var(--sp-border);
      border-radius: 14px;
      cursor: pointer;
      transition: border-color var(--sp-duration-fast) ease, background-color var(--sp-duration-fast) ease;

      &:hover {
        color: var(--sp-brand-strong);
        background: var(--sp-brand-soft);
        border-color: color-mix(in srgb, var(--sp-brand) 38%, var(--sp-border));
      }

      .captcha-visual {
        width: 160px;
        height: 48px;
        position: relative;
        display: grid;
        place-items: center;
        flex: 0 0 160px;
        overflow: hidden;
        border-radius: 11px;
        background: var(--sp-surface);
      }

      .captcha-img {
        width: 100%;
        height: 100%;
      }

      .captcha-status {
        position: absolute;
        inset: 0;
        display: grid;
        place-items: center;
        padding: 5px;
        color: var(--sp-text-muted);
        font-size: 11px;
        background: var(--sp-surface);
      }

      .captcha-status.error {
        color: var(--sp-danger);
        background: var(--sp-danger-soft);
      }

      .captcha-tip {
        font-size: 12px;
        user-select: none;
      }
    }

    .action-buttons {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 20px;
      margin-top: 8px;

      .login-btn {
        width: 100%;
        border-radius: 48px;
        padding: 12px 0;
        font-size: 16px;
        font-weight: 500;
        background: var(--sp-gradient-action);
        border: none;
        transition: all 0.3s;
        letter-spacing: 1px;

        &:hover {
          background: var(--sp-gradient-action);
          transform: translateY(-1px);
          box-shadow: var(--sp-shadow-sm);
        }
      }

      .register-link {
        font-size: 14px;
        color: var(--sp-text-secondary);
        transition: color 0.2s;

        &:hover {
          color: var(--sp-brand);
          text-decoration: underline;
        }
      }
    }
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .login-container { padding-inline: 14px; }
  .login-shell { grid-template-columns: 1fr; border-radius: var(--sp-radius-lg); }
  .brand-panel { min-height: auto; gap: 24px; padding: 30px 26px; }
  .brand-panel h2 { font-size: 30px; }
  .brand-copy { margin-top: 14px; }
  .brand-panel > small { display: none; }
  .login-card {
    padding: 20px 10px;
  }
  .card .title {
    font-size: 28px;
  }
  .card .login-form {
    padding: 20px 24px 28px;
  }
}

@media (max-width: 480px) {
  .brand-panel { padding-top: 56px; }
  .capability-list span:nth-child(n+3) { display: none; }
  .card :deep(.el-card__header) { padding-inline: 20px; }
  .card .login-form { padding-inline: 20px; }
}

/* Product-style sign-in layout */
.login-container { padding: calc(var(--sp-header-height) + 34px) 24px 44px; }
.login-shell {
  width: min(1180px, 100%);
  grid-template-columns: minmax(0, 1.08fr) minmax(390px, .92fr);
  background: #fff;
  border: 1px solid #e5e5ea;
  border-radius: var(--sp-radius-xl);
  box-shadow: var(--sp-shadow-md);
}
.brand-panel {
  min-height: 680px;
  display: grid;
  grid-template-rows: auto auto minmax(220px, 1fr) auto auto;
  justify-content: initial;
  gap: 24px;
  padding: 44px;
  color: var(--sp-text);
  background: #f5f5f7;
}
.brand-panel::before { display: none; }
.brand-mark { color: var(--sp-text-secondary); font-size: 12px; font-weight: 650; letter-spacing: -.01em; }
.brand-mark span { width: 34px; height: 34px; color: var(--sp-on-brand); font-size: 12px; background: var(--sp-brand); border-radius: 10px; box-shadow: none; }
.brand-intro { margin-top: 4px; }
.brand-kicker { margin-bottom: 12px; color: var(--sp-brand); font-size: 13px; font-weight: 650; letter-spacing: 0; }
.brand-panel h2 { color: var(--sp-text); font-size: clamp(42px, 4.2vw, 60px); font-weight: 700; line-height: 1.02; letter-spacing: -.06em; }
.brand-panel h2 em { color: var(--sp-text-muted); }
.brand-copy { max-width: 570px; margin-top: 18px; color: var(--sp-text-secondary); font-size: 14px; line-height: 1.7; }
.brand-visual { position: relative; min-height: 230px; overflow: hidden; border-radius: 24px; background: #e8e8ed; }
.brand-visual :deep(img), .brand-visual :deep(.smart-image-fallback) { width: 100%; height: 100%; min-height: 230px; }
.brand-visual::after { content: ''; position: absolute; inset: 45% 0 0; background: linear-gradient(transparent, rgb(0 0 0 / 36%)); pointer-events: none; }
.brand-visual > div { position: absolute; z-index: 2; right: 16px; bottom: 16px; left: 16px; display: grid; gap: 2px; padding: 11px 13px; color: #fff; background: rgb(0 0 0 / 38%); border: 1px solid rgb(255 255 255 / 28%); border-radius: 15px; backdrop-filter: blur(18px); }
.brand-visual small { color: rgb(255 255 255 / 78%); font-size: 10px; }
.brand-visual strong { font-size: 13px; font-weight: 650; }
.capability-list { gap: 7px; }
.capability-list span { padding: 7px 11px; color: var(--sp-text-secondary); font-size: 11px; background: #fff; border: 1px solid #d9d9de; }
.brand-panel > small { color: var(--sp-text-muted); font-size: 10px; }
.login-card { padding: 44px 30px; background: #fff; backdrop-filter: none; animation: fadeInUp .5s var(--sp-ease-expressive); }
.card .card-header { text-align: left; }
.card .card-header .title { color: var(--sp-text); font-size: 38px; font-weight: 700; letter-spacing: -.045em; }
.card .card-header .subtitle { color: var(--sp-text-muted); }
.card .login-form .custom-input :deep(.el-input__wrapper) { min-height: 50px; padding: 3px 15px; background: #f5f5f7; border-color: #d2d2d7; }
.card .login-form .captcha-button { min-height: 58px; background: #f5f5f7; border-color: #d2d2d7; border-radius: var(--sp-radius-sm); }
.card .login-form .action-buttons .login-btn { min-height: 48px; font-weight: 650; background: var(--sp-gradient-action); box-shadow: 0 8px 20px rgb(0 102 204 / 18%); }

@media (max-width: 820px) {
  .login-container { padding-inline: 12px; }
  .login-shell { grid-template-columns: 1fr; border-radius: var(--sp-radius-lg); }
  .brand-panel { min-height: auto; grid-template-rows: auto auto auto auto; padding: 34px 28px; }
  .brand-panel h2 { font-size: 42px; }
  .brand-visual { min-height: 210px; }
  .brand-panel > small { display: none; }
  .login-card { padding: 26px 12px; }
}

@media (max-width: 480px) {
  .brand-panel { padding: 34px 20px 22px; }
  .brand-panel h2 { font-size: 40px; }
  .brand-visual { min-height: 180px; }
  .brand-visual :deep(img), .brand-visual :deep(.smart-image-fallback) { min-height: 180px; }
  .login-card { padding: 18px 4px; }
}
</style>
