<template>
  <div class="recipe-detail-view">
    <!-- 返回按钮 -->
    <div class="recipe-header">
      <div>
        <span class="sp-eyebrow">菜谱与营养信息</span>
        <h1>{{ recipe.name || '菜谱详情' }}</h1>
      </div>
      <el-button
        plain
        :icon="ArrowLeft"
        @click="goBack"
        class="back-button"
      >
        返回列表
      </el-button>
    </div>

    <div v-loading="loading" class="recipe-content">
      <div v-if="recipe.id" class="recipe-container">
        <!-- 菜谱图片 -->
        <div class="recipe-image">
          <SmartImage
            v-if="isRenderableRecipeImage(recipe.imageUrl) && !imageFailed"
            :src="recipe.imageUrl"
            :alt="recipe.name"
            priority
            class="main-image"
            @error="imageFailed = true"
          />
          <div v-else class="no-image">
            <el-icon :size="60"><Picture /></el-icon>
            <strong>菜谱图片暂不可用</strong>
            <span>详细营养与烹饪信息仍可正常查看</span>
          </div>
        </div>

        <!-- 菜谱基本信息 -->
        <div class="recipe-info">
          <div class="info-section">
            <h3>菜谱介绍</h3>
            <p>{{ recipe.description || '暂无菜谱介绍' }}</p>
          </div>

          <div class="info-section" v-if="recipe.nutritionInfo">
            <h3>营养成分</h3>
            <p>{{ recipe.nutritionInfo }}</p>
          </div>

          <div class="info-section" v-if="recipe.suitableCrowd">
            <h3>适合人群</h3>
            <p>{{ recipe.suitableCrowd }}</p>
          </div>

          <div class="info-section" v-if="recipe.tips">
            <h3>烹饪小贴士</h3>
            <p>{{ recipe.tips }}</p>
          </div>
        </div>
      </div>

      <!-- 烹饪步骤 -->
      <div v-if="cookingSteps.length > 0" class="cooking-steps">
        <h3>烹饪步骤</h3>
        <div class="steps-list">
          <div v-for="(step, index) in cookingSteps" :key="index" class="step-item">
            <div class="step-number">{{ index + 1 }}</div>
            <div class="step-content">{{ step }}</div>
          </div>
        </div>
      </div>

      <!-- 预订区域 -->
      <div v-if="recipe.id" class="order-section">
        <h3><el-icon><ShoppingCart /></el-icon> 预订用餐</h3>
        <div class="order-form">
          <el-form
            ref="orderFormRef"
            :model="orderForm"
            :rules="orderRules"
            label-width="80px"
            class="order-form-container"
          >
            <el-form-item label="预订时间" prop="orderTime">
              <el-date-picker
                v-model="orderForm.orderTime"
                type="datetime"
                placeholder="选择预订时间"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm:ss"
                :disabled-date="disabledDate"
                :shortcuts="shortcuts"
                class="order-time-input"
              />
            </el-form-item>

            <el-form-item label="用餐人数" prop="peopleCount">
              <el-input-number
                v-model="orderForm.peopleCount"
                :min="1"
                :max="20"
                placeholder="请输入用餐人数"
                class="people-input"
              />
            </el-form-item>

            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="orderForm.remark"
                type="textarea"
                :rows="3"
                placeholder="请输入特殊要求、忌口等备注信息"
                maxlength="200"
                show-word-limit
                class="remark-input"
              />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                @click="handleOrder"
                :loading="ordering"
                size="large"
                class="order-button"
              >
                <el-icon><ShoppingCart /></el-icon>
                立即预订
              </el-button>

              <el-button
                type="success"
                @click="viewMyOrders"
                class="my-orders-button"
              >
                <el-icon><Tickets /></el-icon>
                查看我的预订
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <el-empty v-if="!loading && !recipe.id" description="未找到该菜谱，可能已下架">
        <el-button type="primary" @click="goBack">返回菜谱列表</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Picture, ShoppingCart, Tickets } from '@element-plus/icons-vue'
import api from '@/utils/axios'
import { isRenderableRecipeImage } from '@/utils/media'
import SmartImage from '@/components/common/SmartImage.vue'

const route = useRoute()
const router = useRouter()

const recipe = ref({})
const loading = ref(false)
const ordering = ref(false)
const orderFormRef = ref()
const imageFailed = ref(false)

// 预订表单
const orderForm = reactive({
  orderTime: '',
  peopleCount: 1,
  remark: ''
})

// 表单验证规则
const orderRules = {
  orderTime: [
    { required: true, message: '请选择预订时间', trigger: 'change' }
  ],
  peopleCount: [
    { required: true, message: '请输入用餐人数', trigger: 'blur' }
  ]
}

// 快捷时间选择
const shortcuts = [
  {
    text: '明天 10:00',
    value: () => {
      const now = new Date()
      now.setDate(now.getDate() + 1)
      now.setHours(10, 0, 0, 0)
      return now
    }
  },
  {
    text: '明天 12:00',
    value: () => {
      const now = new Date()
      now.setDate(now.getDate() + 1)
      now.setHours(12, 0, 0, 0)
      return now
    }
  },
  {
    text: '后天 10:00',
    value: () => {
      const now = new Date()
      now.setDate(now.getDate() + 2)
      now.setHours(10, 0, 0, 0)
      return now
    }
  }
]

// 禁止选择今天之前的时间
const disabledDate = (time) => {
  return time.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

// 解析烹饪步骤
const cookingSteps = computed(() => {
  if (!recipe.value.cookingSteps) return []
  return recipe.value.cookingSteps.split('\n').filter(step => step.trim())
})

// 获取菜谱详情
const fetchRecipeDetail = async () => {
  try {
    loading.value = true
    const recipeId = route.params.id

    if (!recipeId) {
      ElMessage.error('菜谱ID无效')
      return
    }

    const { data } = await api.get(`/recipe/${recipeId}`)

    if (data.code === 200) {
      recipe.value = data.result || {}
      imageFailed.value = false
    } else {
      ElMessage.error(data.msg || '获取菜谱详情失败')
    }
  } catch (error) {
    if (error.response?.status !== 401) {
      ElMessage.error(error.response?.data?.msg || '获取菜谱详情失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

// 处理预订
const handleOrder = async () => {
  if (!orderFormRef.value) return

  try {
    await orderFormRef.value.validate()

    ordering.value = true

    const orderData = {
      recipeId: recipe.value.id,
      recipeName: recipe.value.name,
      orderTime: orderForm.orderTime,
      peopleCount: orderForm.peopleCount,
      remark: orderForm.remark
    }

    const { data } = await api.post('/recipe-order/order', orderData)

    if (data.code === 200) {
      ElMessage.success('预订成功！')

      // 重置表单
      orderForm.orderTime = ''
      orderForm.peopleCount = 1
      orderForm.remark = ''

      // 询问是否查看我的预订
      ElMessageBox.confirm('预订成功！是否立即查看您的预订记录？', '预订成功', {
        confirmButtonText: '查看我的预订',
        cancelButtonText: '继续浏览',
        type: 'success'
      }).then(() => {
        // 跳转到我的预订页面
        router.push('/front/recipe/MyRecipeOrders')
      }).catch(() => {
        // 用户选择继续浏览
      })
    } else {
      ElMessage.error(data.msg || '预订失败，请稍后重试')
    }
  } catch (error) {
    if (error.name !== 'Error') {
      ElMessage.error('网络错误，请稍后重试')
    }
  } finally {
    ordering.value = false
  }
}

// 查看我的预订
const viewMyOrders = () => {
  const token = sessionStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push('/front/recipe/MyRecipeOrders')
}

// 返回列表
const goBack = () => {
  router.push('/front/recipe/RecipeListView')
}

// 设置默认预订时间（明天10:00）
const setDefaultOrderTime = () => {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  tomorrow.setHours(10, 0, 0, 0)
  // 格式化为YYYY-MM-DD HH:mm:ss格式
  const year = tomorrow.getFullYear()
  const month = (tomorrow.getMonth() + 1).toString().padStart(2, '0')
  const day = tomorrow.getDate().toString().padStart(2, '0')
  const hours = tomorrow.getHours().toString().padStart(2, '0')
  const minutes = tomorrow.getMinutes().toString().padStart(2, '0')
  const seconds = tomorrow.getSeconds().toString().padStart(2, '0')
  orderForm.orderTime = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 生命周期
onMounted(() => {
  fetchRecipeDetail()
  setDefaultOrderTime()
})
</script>

<style scoped>
.recipe-detail-view {
  width: min(var(--sp-content-max), calc(100% - 40px));
  min-height: calc(100vh - var(--sp-header-height));
  padding: 40px 0 56px;
  margin: 0 auto;
}

.recipe-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--sp-border);
}

.recipe-header h1 {
  margin: 8px 0 0;
  color: var(--sp-text);
  font-size: clamp(26px, 3vw, 38px);
  font-weight: 780;
  line-height: 1.18;
  letter-spacing: -.035em;
}

.recipe-content {
  min-height: 360px;
}

.recipe-container { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(320px, .95fr); gap: 24px; padding: 22px; background: var(--sp-surface); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-sm); }

.recipe-image {
  width: 100%;
  height: 400px;
  overflow: hidden;
  border-radius: var(--sp-radius-md);
  background-color: var(--sp-surface-muted);
  border: 1px solid var(--sp-border);
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--sp-text-muted);
  font-size: 16px;
  gap: 7px;
  text-align: center;
}

.no-image strong { color: var(--sp-text-secondary); font-size: 15px; }
.no-image span { max-width: 220px; color: var(--sp-text-muted); font-size: 12px; line-height: 1.55; }

.no-image .el-icon {
  font-size: 60px;
  margin-bottom: 10px;
  color: var(--sp-border-strong);
}

.recipe-info {
  height: 400px;
  overflow-y: auto;
  padding: 4px 10px 4px 2px;
  scrollbar-color: var(--sp-border-strong) transparent;
}

.info-section {
  margin-bottom: 20px;
}

.info-section h3 {
  font-size: 16px;
  color: var(--sp-text);
  font-weight: 720;
  margin: 0 0 8px;
  padding-bottom: 5px;
  border-bottom: 1px solid var(--sp-border);
}

.info-section p {
  color: var(--sp-text-secondary);
  line-height: 1.75;
  margin: 0;
}

.cooking-steps {
  margin-top: 24px;
  padding: 24px;
  background-color: var(--sp-surface);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-lg);
  box-shadow: var(--sp-shadow-sm);
}

.cooking-steps h3 {
  font-size: 18px;
  color: var(--sp-text);
  font-weight: 740;
  margin: 0 0 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--sp-border);
}

.steps-list {
  max-width: 800px;
  margin: 0 auto;
}

.step-item {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.step-number {
  width: 30px;
  height: 30px;
  background-color: var(--sp-brand);
  color: var(--sp-on-brand);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  margin-right: 15px;
  flex-shrink: 0;
}

.step-content {
  flex: 1;
  color: var(--sp-text-secondary);
  background-color: var(--sp-surface-muted);
  border: 1px solid var(--sp-border);
  padding: 15px;
  border-radius: 6px;
  box-shadow: var(--sp-shadow-xs);
  line-height: 1.6;
}

/* 预订区域样式 */
.order-section {
  margin-top: 24px;
  padding: 30px;
  background: linear-gradient(145deg, var(--sp-info-soft), var(--sp-surface));
  border-radius: var(--sp-radius-lg);
  border: 1px solid color-mix(in srgb, var(--sp-info) 24%, var(--sp-border));
}

.order-section h3 {
  font-size: 20px;
  color: var(--sp-info);
  font-weight: 600;
  margin: 0 0 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--sp-info) 24%, var(--sp-border));
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-form-container {
  max-width: 600px;
  margin: 0 auto;
}
.order-time-input { width: min(100%, 320px) !important; }
.people-input { width: min(100%, 220px) !important; }
.remark-input { width: min(100%, 440px) !important; }

.order-button {
  font-size: 16px;
  padding: 12px 24px;
  color: var(--sp-on-brand);
  background: var(--sp-brand);
  border: none;
  transition: transform var(--sp-duration-fast) ease, box-shadow var(--sp-duration-fast) ease;
}

.order-button:hover {
  background: var(--sp-brand-strong);
  transform: translateY(-2px);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--sp-brand) 26%, transparent);
}

.my-orders-button {
  font-size: 16px;
  padding: 12px 24px;
  transition: transform var(--sp-duration-fast) ease, box-shadow var(--sp-duration-fast) ease;
}

.my-orders-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--sp-success) 22%, transparent);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .recipe-detail-view { width: min(100% - 24px, 720px); padding: 24px 0 40px; }
  .recipe-header { align-items: flex-start; flex-direction: column; }
  .recipe-container { grid-template-columns: 1fr; padding: 14px; }
  .recipe-image {
    height: 300px;
  }

  .recipe-info {
    height: auto;
    margin-top: 20px;
  }

  .order-section {
    padding: 20px 15px;
  }

  .order-form-container {
    width: 100%;
  }

  .order-section :deep(.el-form-item) { align-items: stretch; flex-direction: column; }
  .order-section :deep(.el-form-item__label) { width: auto !important; justify-content: flex-start; }
  .order-section :deep(.el-form-item__content) { margin-left: 0 !important; }
}
</style>
