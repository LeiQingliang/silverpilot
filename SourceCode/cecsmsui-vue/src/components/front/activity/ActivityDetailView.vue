<template>
  <div class="detail-container">
    <div class="detail-heading">
      <span class="sp-eyebrow">活动信息</span>
      <h1 class="sp-page-title">活动详情</h1>
      <p class="sp-page-copy">核对时间、地点与负责人后完成报名，所有状态均来自当前业务数据。</p>
    </div>
    <section v-if="!validActivityId || loadError" class="detail-empty" role="status">
      <span aria-hidden="true">i</span>
      <div>
        <h2>{{ validActivityId ? '活动信息暂时不可用' : '请先选择要查看的活动' }}</h2>
        <p>{{ loadError || '从活动列表进入详情页，即可查看时间、地点与报名状态。' }}</p>
      </div>
      <el-button type="primary" @click="backToActivities">返回活动列表</el-button>
    </section>
    <el-card v-else v-loading="loading" class="detail-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">活动信息卡</span>
          <el-tag effect="plain" round>{{ isAfterNow ? '开放报名' : '活动已结束' }}</el-tag>
        </div>
      </template>

      <div class="detail-content">
        <el-row :gutter="30">
          <!-- 左侧图片区域 -->
          <el-col :xs="24" :sm="24" :md="12" :lg="12">
            <div class="image-wrapper">
              <SmartImage :src="image" :alt="activityName || '养老活动图片'" fit="cover" class="activity-image" fallback-label="暂无活动图片" />
            </div>
          </el-col>

          <!-- 右侧信息区域 -->
          <el-col :xs="24" :sm="24" :md="12" :lg="12">
            <el-descriptions :title="activityName" :column="1" class="custom-descriptions">
              <template #extra>
                <el-button
                  type="primary"
                  v-if="isAfterNow"
                  @click="toSignup"
                  :disabled="disabled"
                  class="action-btn"
                >
                  {{ form.state }}
                </el-button>
                <el-button v-else type="info" disabled class="action-btn">活动已结束</el-button>
              </template>

              <el-descriptions-item label="活动时间">
                <span>{{ descriptionData.activityDate || '待公布' }}</span>
                <span class="time-range">{{ descriptionData.startTime || '--:--' }} – {{ descriptionData.endTime || '--:--' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="活动地点">
                {{ descriptionData.activityAddress || '待公布' }}
              </el-descriptions-item>
              <el-descriptions-item label="负责人">
                {{ director.name || '待安排' }} <span class="time-range">{{ director.telephone || '' }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import $axios from '../../../utils/axios'
import { ElNotification } from 'element-plus'
import SmartImage from '@/components/common/SmartImage.vue'

const router = useRouter()
const route = useRoute()
const descriptionData = ref({})
const director = ref({})
const activityDate = ref('')
const activityTime = ref('')
const activityName = ref('')
const activityId = computed(() => Number(route.query.Id))
const validActivityId = computed(() => Number.isInteger(activityId.value) && activityId.value > 0)
const image = ref()
const storedUser = JSON.parse(sessionStorage.getItem('user') || '{}')
const uId = storedUser.id
const form = ref({ state: '报名' })
const disabled = ref(false)
const loading = ref(false)
const loadError = ref('')
const backToActivities = () => router.push('/front/activity/FrontActivityView')

const pre4Init = async () => {
  const path = '/userActivity/selectByuIdByaId/' + uId + '/' + activityId.value
  const { data: res } = await $axios.get(path)
  if (res.code === 200) {
    form.value = res.result
    disabled.value = true
  } else if (res.code === 100 && isAfterNow.value) {
    disabled.value = false
    form.value.state = "报名"
  }
}

const init = async () => {
  if (!validActivityId.value) return
  loading.value = true
  loadError.value = ''
  try {
    const path = '/activity/selectById/' + activityId.value
    const { data: res } = await $axios.get(path)
    if (res.code === 200) {
      descriptionData.value = res.result || {}
      director.value = descriptionData.value.director || {}
      activityDate.value = descriptionData.value.activityDate
      activityTime.value = descriptionData.value.startTime
      activityName.value = descriptionData.value.activityName
      image.value = descriptionData.value.image
      if (uId) await pre4Init()
    } else {
      loadError.value = res.msg || '暂时无法获取活动详情。'
    }
  } catch (error) {
    if (error.response?.status !== 401) {
      loadError.value = '活动详情暂时不可用，请稍后重试。'
    }
  } finally {
    loading.value = false
  }
}
init()

const compare = computed(() => {
  if (!activityDate.value || !activityTime.value) return new Date(0)
  const date = `${activityDate.value}T${activityTime.value}`
  return new Date(date)
})
const now = computed(() => new Date())
const isAfterNow = computed(() => compare.value > now.value)

const pre4Sign = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hour = String(now.getHours()).padStart(2, '0')
  const minute = String(now.getMinutes()).padStart(2, '0')
  const second = String(now.getSeconds()).padStart(2, '0')
  form.value.enterDate = `${year}-${month}-${day}`
  form.value.enterTime = `${hour}:${minute}:${second}`
  form.value.uId = uId
  form.value.aId = activityId.value
}

const toSignup = async () => {
  pre4Sign()
  const path = '/userActivity/insert'
  const { data: res } = await $axios.put(path, form.value)
  if (res.code === 200) {
    init()
    ElNotification({
      title: '成功',
      message: '报名成功！',
      type: 'success',
    })
  } else {
    ElNotification({
      title: '失败',
      message: res.msg || '报名失败，请稍后重试！',
      type: 'error',
    })
  }
}
</script>

<style scoped>
.detail-container {
  width: min(var(--sp-content-max), calc(100% - 40px));
  min-height: calc(100vh - var(--sp-header-height));
  margin: 0 auto;
  padding: 40px 0 56px;
}
.detail-heading { margin-bottom: 22px; }

.detail-empty {
  min-height: 280px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 28px;
  color: var(--sp-text);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-lg);
  box-shadow: var(--sp-shadow-sm);
}
.detail-empty > span { width: 46px; height: 46px; display: grid; place-items: center; color: var(--sp-brand-strong); font-size: 20px; font-weight: 800; background: var(--sp-brand-soft); border-radius: 14px; }
.detail-empty h2 { margin: 0 0 5px; font-size: 20px; }
.detail-empty p { margin: 0; color: var(--sp-text-secondary); }

.detail-card {
  width: 100%;
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-lg);
  background: var(--sp-surface);
  box-shadow: var(--sp-shadow-md);
  overflow: hidden;
}

.detail-card :deep(.el-card__header) {
  padding: 20px 28px;
  border-bottom: 1px solid var(--sp-border);
  background: var(--sp-surface-muted);
}
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }

.card-header .header-title {
  font-size: 19px;
  font-weight: 760;
  color: var(--sp-text);
  position: relative;
  display: inline-block;
}

.card-header .header-title::after {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 0;
  width: 40px;
  height: 3px;
  background: linear-gradient(90deg, var(--sp-brand), var(--sp-accent));
  border-radius: 3px;
}

.detail-content {
  padding: 12px 28px 28px;
}

/* 图片区域 */
.image-wrapper {
  width: 100%;
  border-radius: var(--sp-radius-md);
  overflow: hidden;
  background: var(--sp-surface-muted);
  border: 1px solid var(--sp-border);
  box-shadow: var(--sp-shadow-sm);
}

.activity-image {
  width: 100%;
  height: auto;
  min-height: 300px;
  object-fit: cover;
  display: block;
}
.image-fallback { min-height: 300px; display: grid; place-items: center; color: var(--sp-text-muted); background: var(--sp-surface-muted); }

/* 描述区域 */
.custom-descriptions {
  margin-top: 8px;
}

.custom-descriptions :deep(.el-descriptions__title) {
  font-size: 22px;
  font-weight: 600;
  color: var(--sp-text);
  margin-bottom: 20px;
}

.custom-descriptions :deep(.el-descriptions__body) {
  background: transparent;
}

.custom-descriptions :deep(.el-descriptions__label) {
  font-weight: 600;
  color: var(--sp-brand-strong);
  background: var(--sp-brand-soft);
  border-radius: 20px;
  padding: 8px 16px;
  width: 80px;
}

.custom-descriptions :deep(.el-descriptions__content) {
  background: transparent;
  padding: 8px 16px;
  color: var(--sp-text-secondary);
}

.time-range {
  margin-left: 8px;
  color: var(--sp-text-muted);
  font-size: 13px;
}

/* 按钮样式 */
.action-btn {
  border-radius: 48px;
  padding: 10px 28px;
  font-size: 15px;
  font-weight: 500;
  transition: transform var(--sp-duration-fast) ease, box-shadow var(--sp-duration-fast) ease;
  box-shadow: var(--sp-shadow-xs);
}

.action-btn.el-button--primary {
  color: var(--sp-on-brand);
  background: var(--sp-brand);
  border: none;
}

.action-btn.el-button--primary:hover {
  background: var(--sp-brand-strong);
  transform: translateY(-2px);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--sp-brand) 28%, transparent);
}

.action-btn.is-disabled {
  background: var(--sp-surface-muted);
  border-color: var(--sp-border);
  color: var(--sp-text-muted);
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .detail-container {
    width: min(100% - 24px, 720px);
    padding: 24px 0 40px;
  }
  .detail-content {
    padding: 8px 16px 16px;
  }
  .detail-empty { grid-template-columns: auto minmax(0, 1fr); padding: 20px; }
  .detail-empty .el-button { grid-column: 1 / -1; width: 100%; }
  .image-wrapper {
    margin-bottom: 20px;
  }
  .custom-descriptions :deep(.el-descriptions__title) {
    font-size: 20px;
  }
  .action-btn {
    padding: 8px 20px;
    font-size: 14px;
  }
}
</style>
