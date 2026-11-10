<template>
  <div class="register-page">
    <FrontHeaderMenu />
    <div class="register-container">
      <div class="register-shell">
        <aside class="trust-panel" aria-labelledby="register-trust-title">
          <div class="trust-mark"><span>银</span><strong>智慧养老成员服务</strong></div>
          <div>
            <p class="sp-eyebrow">成员信息登记</p>
            <h1 id="register-trust-title">创建账号<br />体验养老服务</h1>
            <p>本项目为公开演示，使用虚构测试资料体验活动、预约与生活助理。请勿提交真实身份、联系方式或健康信息。</p>
          </div>
          <ul>
            <li><span>01</span>公开演示仅填写虚构测试资料</li>
            <li><span>02</span>密码由后端安全哈希存储</li>
            <li><span>03</span>智能建议不替代医疗诊断</li>
          </ul>
        </aside>

        <el-card class="register-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="sp-eyebrow">创建账号</span>
              <h2>创建成员账号</h2>
              <p>仅填写虚构测试资料，请勿使用真实个人信息</p>
            </div>
          </template>

          <el-form
            ref="ruleFormRef"
            :model="form"
            :rules="rules"
            label-position="top"
            status-icon
            class="register-form"
            aria-describedby="privacy-note"
            @submit.prevent="onSubmit(ruleFormRef)"
          >
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="用户名" prop="username">
                  <el-input v-model="form.username" placeholder="5-32位字母、数字或下划线" autocomplete="username" maxlength="32" clearable />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="演示姓名" prop="name">
                  <el-input v-model="form.name" placeholder="请输入虚构的演示姓名" autocomplete="off" maxlength="50" clearable />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="密码" prop="password">
                  <el-input v-model="form.password" type="password" placeholder="8-72位独立密码" autocomplete="new-password" maxlength="72" show-password clearable />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="确认密码" prop="checkPass">
                  <el-input v-model="form.checkPass" type="password" placeholder="请再次输入密码" autocomplete="new-password" show-password clearable />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="测试证件号码" prop="idNum">
                  <el-input v-model="form.idNum" placeholder="虚构18位测试号码，勿填真实证件" autocomplete="off" maxlength="18" clearable />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="测试手机号码" prop="telephone">
                  <el-input v-model="form.telephone" placeholder="虚构11位测试号码" autocomplete="off" inputmode="tel" maxlength="11" clearable />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="图形验证码" prop="verificationCode">
              <div class="registration-captcha">
                <el-input v-model.trim="form.verificationCode" placeholder="输入图中4位字符" autocomplete="off" maxlength="4" clearable />
                <button class="captcha-button" type="button" :disabled="submitting" aria-label="刷新注册验证码" @click="refreshCaptcha">
                  <img v-if="!captchaError" :key="captchaId" :src="captchaUrl" alt="注册验证码" width="120" height="40" @load="onCaptchaLoad" @error="onCaptchaError" />
                  <span v-if="captchaError" role="status">加载失败，点击重试</span>
                  <span v-else>{{ captchaLoading ? '加载中…' : '看不清？点击刷新' }}</span>
                </button>
              </div>
            </el-form-item>
            <p id="privacy-note" class="privacy-note">这是公开演示网站，资料会用于共享演示。请勿填写真实姓名、手机号、身份证号、地址或健康信息；请勿复用其他网站的密码。</p>
            <div class="action-buttons">
              <el-button native-type="submit" type="primary" :loading="submitting" :disabled="captchaLoading || captchaError">创建账号</el-button>
              <el-button :disabled="submitting" @click="resetForm(ruleFormRef)">重置</el-button>
            </div>
            <button class="login-link" type="button" @click="toLogin">已有账号？返回登录</button>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import FrontHeaderMenu from '@/components/front/home/FrontHeaderMenu.vue'
import api, { apiUrl } from '../utils/axios'
import { createRegistrationPayload } from '../utils/registration'

const router = useRouter()
const ruleFormRef = ref()
const form = ref({ username: '', name: '', password: '', checkPass: '', idNum: '', telephone: '', verificationCode: '' })
const submitting = ref(false)
let redirectTimer
const createCaptchaId = () => globalThis.crypto?.randomUUID?.()
  || `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
const captchaId = ref(createCaptchaId())
const captchaUrl = ref(apiUrl('/user/getVerificationCode/' + captchaId.value))
const captchaLoading = ref(true)
const captchaError = ref(false)

const refreshCaptcha = () => {
  captchaId.value = createCaptchaId()
  captchaUrl.value = apiUrl('/user/getVerificationCode/' + captchaId.value)
  form.value.verificationCode = ''
  captchaLoading.value = true
  captchaError.value = false
  ruleFormRef.value?.clearValidate('verificationCode')
}
const onCaptchaLoad = () => { captchaLoading.value = false; captchaError.value = false }
const onCaptchaError = () => { captchaLoading.value = false; captchaError.value = true }

const validatePass2 = (_rule, value, callback) => {
  if (!value) callback(new Error('请再次输入密码'))
  else if (value !== form.value.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = reactive({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{5,32}$/, message: '5-32位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码需8-72位，请使用独立密码', trigger: 'blur' }
  ],
  checkPass: [{ validator: validatePass2, required: true, trigger: 'change' }],
  idNum: [
    { required: true, message: '请输入虚构测试证件号码', trigger: 'blur' },
    { pattern: /^\d{17}[0-9Xx]$/, message: '测试号码需18位，末位可为X', trigger: 'change' }
  ],
  name: [{ required: true, message: '请输入虚构演示姓名', trigger: 'blur' }],
  telephone: [
    { required: true, message: '请输入虚构测试手机号码', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '测试号码需符合11位手机号格式', trigger: 'change' }
  ],
  verificationCode: [
    { required: true, message: '请输入图形验证码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{4}$/, message: '请输入图中的4位字符', trigger: 'blur' }
  ]
})

const onSubmit = async (formEl) => {
  if (!formEl || submitting.value) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) { refreshCaptcha(); return }
  if (captchaLoading.value || captchaError.value) {
    ElMessage.error('请先加载验证码后再提交')
    refreshCaptcha()
    return
  }
  submitting.value = true
  try {
    const payload = createRegistrationPayload(form.value, captchaId.value)
    const { data } = await api.put('/user/register', payload, { suppressErrorToast: true })
    const messages = { 101: '注册失败，请重试', 104: '用户名已被注册', 105: '测试号码已被使用，请更换', 108: '验证码错误或已过期，请重新输入', 429: '注册尝试过于频繁，请稍后重试' }
    if (data.code !== 200) {
      ElMessage.error(messages[data.code] || data.msg || '注册失败')
      return
    }
    ElMessage.success('注册成功，即将返回登录页')
    redirectTimer = window.setTimeout(() => router.push('/login'), 1200)
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || '注册请求失败，请检查网络后重试')
  } finally {
    submitting.value = false
    refreshCaptcha()
  }
}

const resetForm = (formEl) => { formEl?.resetFields(); refreshCaptcha() }
const toLogin = () => router.push('/login')
onBeforeUnmount(() => window.clearTimeout(redirectTimer))
</script>

<style scoped>
.register-container { position: relative; min-height: 100vh; display: grid; place-items: center; overflow: hidden; padding: calc(var(--sp-header-height) + 34px) 22px 48px; background: transparent; }
.register-shell { position: relative; z-index: 1; width: min(1120px, 100%); display: grid; grid-template-columns: minmax(320px, .78fr) minmax(0, 1.22fr); overflow: hidden; background: rgb(255 255 255 / 94%); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-xl); box-shadow: var(--sp-shadow-lg); backdrop-filter: blur(24px); }
.trust-panel { position: relative; isolation: isolate; min-height: 640px; display: flex; flex-direction: column; justify-content: space-between; gap: 30px; overflow: hidden; padding: 46px; color: var(--sp-on-night); background: var(--sp-gradient-aurora); }
.trust-panel::before { content: ''; position: absolute; z-index: -1; width: 380px; aspect-ratio: 1; top: -190px; right: -150px; border: 1px solid rgb(255 255 255 / 14%); border-radius: 50%; box-shadow: 0 0 0 52px rgb(255 255 255 / 3%), 0 0 0 104px rgb(255 255 255 / 2%); animation: sp-ambient-drift 14s var(--sp-ease-standard) infinite alternate; }
.trust-mark { display: flex; align-items: center; gap: 10px; font-size: 11px; font-weight: 750; letter-spacing: .06em; }.trust-mark span { width: 36px; height: 36px; display: grid; place-items: center; color: var(--sp-night); font-size: 14px; background: var(--sp-energy-lime); border-radius: 50%; box-shadow: 0 0 28px rgb(217 255 116 / 22%); }
.trust-panel .sp-eyebrow { color: var(--sp-on-night-muted); }.trust-panel h1 { margin: 12px 0 16px; font-size: clamp(38px, 4.5vw, 58px); line-height: 1.05; letter-spacing: -.05em; }.trust-panel p:not(.sp-eyebrow) { margin: 0; color: var(--sp-on-night-muted); font-size: 13px; line-height: 1.8; }
.trust-panel ul { display: grid; gap: 10px; list-style: none; }.trust-panel li { display: flex; align-items: center; gap: 10px; color: var(--sp-on-night-muted); font-size: 12px; }.trust-panel li span { color: var(--sp-energy-lime); font-size: 10px; font-weight: 800; }
.register-card { display: grid; align-content: center; border: 0; border-radius: 0; box-shadow: none; }
.register-card :deep(.el-card__header) { padding: 34px 38px 10px; border: 0; }.register-card :deep(.el-card__body) { padding: 10px 38px 34px; }
.card-header { display: grid; gap: 6px; }.card-header h2 { margin: 0; color: var(--sp-text); font-size: 28px; letter-spacing: -.025em; }.card-header p { margin: 0; color: var(--sp-text-muted); font-size: 13px; }
.register-form :deep(.el-form-item) { margin-bottom: 18px; }.register-form :deep(.el-form-item__label) { margin-bottom: 7px; line-height: 1.2; }
.registration-captcha { display: flex; align-items: center; gap: 12px; width: 100%; }
.registration-captcha .el-input { flex: 1; min-width: 0; }
.captcha-button { display: grid; justify-items: center; gap: 3px; flex: 0 0 136px; min-height: 66px; padding: 5px; border: 1px solid var(--sp-border); border-radius: var(--sp-radius-sm); background: var(--sp-surface-muted); color: var(--sp-text-secondary); font-size: 11px; cursor: pointer; }
.captcha-button img { display: block; max-width: 100%; object-fit: contain; }
.captcha-button:disabled { cursor: wait; }
.privacy-note { margin: 2px 0 18px; padding: 10px 12px; color: var(--sp-text-muted); font-size: 11px; line-height: 1.55; background: var(--sp-surface-muted); border-left: 3px solid var(--sp-brand); border-radius: 0 var(--sp-radius-xs) var(--sp-radius-xs) 0; }
.action-buttons { display: grid; grid-template-columns: 1fr auto; gap: 10px; }.action-buttons .el-button { min-height: 46px; margin: 0; }
.login-link { width: 100%; min-height: 44px; margin-top: 10px; color: var(--sp-brand-strong); font-size: 13px; font-weight: 650; background: transparent; border: 0; border-radius: var(--sp-radius-sm); cursor: pointer; }.login-link:hover { background: var(--sp-brand-soft); }
@media (max-width: 760px) { .register-container { padding-inline: 12px; }.register-shell { grid-template-columns: 1fr; border-radius: var(--sp-radius-lg); }.trust-panel { min-height: auto; padding: 30px 26px; }.trust-panel h1 { font-size: 34px; }.trust-panel ul { display: none; }.register-card :deep(.el-card__header) { padding: 26px 22px 8px; }.register-card :deep(.el-card__body) { padding: 10px 22px 26px; } }

.register-shell { background: #fff; border: 1px solid #e5e5ea; box-shadow: var(--sp-shadow-md); backdrop-filter: none; }
.trust-panel { color: var(--sp-text); background: #f5f5f7; }
.trust-panel::before { display: none; }
.trust-mark { color: var(--sp-text-secondary); letter-spacing: -.01em; }
.trust-mark span { color: var(--sp-on-brand); background: var(--sp-brand); border-radius: 10px; box-shadow: none; }
.trust-panel .sp-eyebrow { color: var(--sp-brand); }
.trust-panel h1 { color: var(--sp-text); font-size: clamp(42px, 4.5vw, 60px); font-weight: 700; letter-spacing: -.06em; }
.trust-panel p:not(.sp-eyebrow) { color: var(--sp-text-secondary); font-size: 14px; }
.trust-panel ul { gap: 8px; }
.trust-panel li { min-height: 48px; padding: 8px 12px; color: var(--sp-text-secondary); background: #fff; border: 1px solid #dedee3; border-radius: var(--sp-radius-sm); }
.trust-panel li span { color: var(--sp-brand); }
.register-card :deep(.el-card__header) { padding-top: 42px; }
.card-header h2 { font-size: 34px; letter-spacing: -.045em; }
.privacy-note { background: var(--sp-brand-soft); border-left-color: var(--sp-brand); }

@media (max-width: 760px) {
  .trust-panel { padding: 34px 24px 26px; }
  .trust-panel h1 { font-size: 40px; }
  .register-card :deep(.el-card__header) { padding-top: 30px; }
}
</style>
