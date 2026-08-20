<template>
  <div class="recipe-list-view">
    <div class="page-header">
      <span class="sp-eyebrow">健康膳食目录</span>
      <h1 class="sp-page-title">健康菜谱</h1>
      <p class="sp-page-copy">浏览经过系统整理的膳食内容，根据个人健康情况咨询专业人员后选择。</p>
    </div>

    <!-- 搜索区域 -->
    <form class="search-area" role="search" @submit.prevent="handleSearch">
      <label class="sr-only" for="recipe-search">搜索菜谱名称</label>
      <el-input
        id="recipe-search"
        v-model="searchText"
        placeholder="搜索菜谱名称"
        clearable
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button native-type="submit" type="primary" :icon="Search">搜索</el-button>
      <el-button @click="resetSearch" :icon="Refresh">重置</el-button>
    </form>

    <!-- 菜谱列表 -->
    <div v-loading="loading" class="recipe-list" element-loading-text="正在读取菜谱">
      <el-row :gutter="20">
        <el-col
          v-for="recipe in recipeList"
          :key="recipe.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
          class="recipe-item"
        >
          <el-card class="recipe-card" shadow="never" role="button" tabindex="0" :aria-label="`查看菜谱：${recipe.name}`" @click="viewRecipeDetail(recipe.id)" @keydown.enter="viewRecipeDetail(recipe.id)" @keydown.space.prevent="viewRecipeDetail(recipe.id)">
            <div class="recipe-image">
              <SmartImage
                v-if="isRenderableRecipeImage(recipe.imageUrl)"
                :src="recipe.imageUrl"
                :alt="recipe.name"
                class="recipe-img"
                fallback-label="菜谱图片"
              />
              <div v-else class="no-image">
                <el-icon><Picture /></el-icon>
              </div>
            </div>
            <div class="recipe-info">
              <h3 class="recipe-name">{{ recipe.name }}</h3>
              <p class="recipe-desc">{{ recipe.description }}</p>
              <div class="recipe-tags">
                <el-tag type="info" size="small">{{ recipe.suitableCrowd }}</el-tag>
              </div>
              <div class="recipe-actions">
                <el-button type="primary" size="small" @click.stop="viewRecipeDetail(recipe.id)">
                  查看详情
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[8, 12, 16, 20]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 空状态 -->
    <div v-if="recipeList.length === 0 && !loading" class="empty-state">
      <el-empty description="暂无菜谱数据" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Picture } from '@element-plus/icons-vue'
import api from '@/utils/axios'
import { isRenderableRecipeImage } from '@/utils/media'
import SmartImage from '@/components/common/SmartImage.vue'

const router = useRouter()

// 响应式数据
const searchText = ref('')
const recipeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(8)
const total = ref(0)

// 获取菜谱列表
const fetchRecipeList = async () => {
  try {
    loading.value = true
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      name: searchText.value
    }
    const { data } = await api.get('/recipe/list', { params })

    if (data.code === 200) {
      recipeList.value = data.result?.records || []
      total.value = data.result?.total || 0
    } else {
      ElMessage.error(data.msg || '获取菜谱列表失败')
    }
  } catch (error) {
    if (error.response?.status !== 401) {
      ElMessage.error(error.response?.data?.msg || '获取菜谱列表失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchRecipeList()
}

// 重置搜索
const resetSearch = () => {
  searchText.value = ''
  currentPage.value = 1
  fetchRecipeList()
}

// 查看菜谱详情
const viewRecipeDetail = (id) => {
  router.push(`/front/recipe/RecipeDetailView/${id}`)
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchRecipeList()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchRecipeList()
}

// 生命周期
onMounted(() => {
  fetchRecipeList()
})
</script>

<style scoped>
.recipe-list-view {
  width: min(var(--sp-content-max), calc(100% - 40px));
  min-height: calc(100vh - var(--sp-header-height));
  padding: 34px 0 48px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 22px;
}

.search-area {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
  padding: 15px;
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-md);
  box-shadow: var(--sp-shadow-xs);
}
.search-area .el-input { width: min(420px, 100%); }

.recipe-list {
  min-height: 430px;
}

.recipe-item {
  margin-bottom: 20px;
}

.recipe-card {
  height: 100%;
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--sp-duration-base) var(--sp-ease-standard), box-shadow var(--sp-duration-base) ease, border-color var(--sp-duration-base) ease;
}

.recipe-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--sp-brand) 34%, var(--sp-border));
  box-shadow: var(--sp-shadow-md);
}
.recipe-card :deep(.el-card__body) { height: 100%; display: flex; flex-direction: column; padding: 0; }

.recipe-image {
  width: 100%;
  height: 180px;
  overflow: hidden;
  background-color: var(--sp-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.recipe-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  font-size: 48px;
  color: var(--sp-text-muted);
}

.recipe-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 15px;
}

.recipe-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--sp-text);
  margin: 0 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recipe-desc {
  font-size: 13px;
  color: var(--sp-text-secondary);
  line-height: 1.6;
  margin: 0 0 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 40px;
}

.recipe-tags {
  margin-bottom: 12px;
}

.recipe-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 30px;
  padding: 16px 0;
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-md);
}

.empty-state {
  margin-top: 100px;
  text-align: center;
}

@media (max-width: 640px) {
  .recipe-list-view { width: calc(100% - 24px); padding-top: 22px; }
  .search-area { align-items: stretch; flex-wrap: wrap; }
  .search-area .el-input { width: 100%; }
  .search-area .el-button { flex: 1; margin: 0; }
}
</style>
