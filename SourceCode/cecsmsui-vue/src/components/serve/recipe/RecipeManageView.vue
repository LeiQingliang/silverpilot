<template>
  <div class="recipe-manage-container">
    <div class="page-header">
      <h1>菜谱管理</h1>
      <el-button
        type="primary"
        :icon="Plus"
        @click="handleCreate"
        class="create-btn"
      >
        新增菜谱
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <div class="filter-area">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="菜谱名称">
          <el-input
            v-model="filterForm.name"
            placeholder="搜索菜谱名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch" :icon="Search">
            搜索
          </el-button>
          <el-button @click="resetFilter" :icon="Refresh">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-area">
      <el-table
        class="desktop-recipe-table"
        v-loading="loading"
        :data="recipeList"
        style="width: 100%"
        border
        stripe
      >
        <el-table-column label="ID" prop="id" width="80" />

        <el-table-column label="菜谱名称" prop="name" min-width="150" />

        <el-table-column label="描述" prop="description" min-width="200">
          <template #default="{ row }">
            <div class="description-cell">
              {{ row.description || '--' }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="图片" width="100">
          <template #default="{ row }">
            <el-image
              v-if="isRenderableRecipeImage(row.imageUrl)"
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              fit="cover"
              class="table-image"
              preview-teleported
            >
              <template #error>
                <div class="image-error">加载失败</div>
              </template>
            </el-image>
            <span v-else>--</span>
          </template>
        </el-table-column>

        <el-table-column label="适宜人群" prop="suitableCrowd" width="120" />

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '已删除' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="handleEdit(row)"
              :icon="Edit"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row)"
              :icon="Delete"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-loading="loading" class="mobile-recipe-list" aria-label="菜谱管理列表">
        <article v-for="recipe in recipeList" :key="recipe.id">
          <header>
            <div>
              <small>RECIPE {{ recipe.id }}</small>
              <h2>{{ recipe.name || '未命名菜谱' }}</h2>
            </div>
            <el-tag :type="recipe.status === 1 ? 'success' : 'info'">
              {{ recipe.status === 1 ? '正常' : '已删除' }}
            </el-tag>
          </header>
          <p>{{ recipe.description || '暂无描述' }}</p>
          <dl>
            <div><dt>适宜人群</dt><dd>{{ recipe.suitableCrowd || '未设置' }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatTime(recipe.createTime) || '--' }}</dd></div>
          </dl>
          <footer>
            <el-button type="primary" plain :icon="Edit" @click="handleEdit(recipe)">编辑</el-button>
            <el-button type="danger" plain :icon="Delete" @click="handleDelete(recipe)">删除</el-button>
          </footer>
        </article>
        <el-empty v-if="!loading && !recipeList.length" description="暂无菜谱数据" :image-size="64" />
      </div>

      <!-- 分页 -->
      <div class="pagination-container" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 30, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getRecipeList, deleteRecipe } from '@/api/recipe'
import { isRenderableRecipeImage } from '@/utils/media'

const router = useRouter()

// 响应式数据
const filterForm = reactive({
  name: ''
})
const recipeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 检查登录状态
const checkLogin = () => {
  const userStr = sessionStorage.getItem('user')
  const token = sessionStorage.getItem('token')

  if (!userStr || !token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return false
  }

  return true
}

// 获取菜谱列表
const fetchRecipes = async () => {
  try {
    if (!checkLogin()) {
      return
    }

    loading.value = true
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      name: filterForm.name
    }

    const { data: res } = await getRecipeList(params)

    if (res && res.code === 200) {
      recipeList.value = res.result?.records || []
      total.value = res.result?.total || 0
    } else {
      const errorMsg = res?.msg || '获取菜谱列表失败'
      ElMessage.error(errorMsg)
    }
  } catch (error) {
    if (error.message === '请重新登录') {
      // 已经在响应拦截器中处理了跳转
    } else {
      ElMessage.error('网络错误，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchRecipes()
}

// 重置筛选
const resetFilter = () => {
  filterForm.name = ''
  currentPage.value = 1
  fetchRecipes()
}

// 新增菜谱
const handleCreate = () => {
  if (!checkLogin()) {
    return
  }
  // 跳转到新增菜谱页面
  router.push('/RecipeEditView')
}

// 编辑菜谱
const handleEdit = (row) => {
  if (!checkLogin()) {
    return
  }
  // 跳转到编辑菜谱页面，传递菜谱ID
  router.push(`/RecipeEditView/${row.id}`)
}

// 删除菜谱
const handleDelete = async (row) => {
  try {
    if (!checkLogin()) {
      return
    }

    await ElMessageBox.confirm(
      `确定要删除菜谱 "${row.name}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const { data: res } = await deleteRecipe(row.id)

    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchRecipes()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请重试')
    }
  }
}

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  fetchRecipes()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchRecipes()
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  try {
    const date = new Date(time)
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch (e) {
    console.error('时间格式化错误:', e)
    return '时间格式错误'
  }
}

// 生命周期
onMounted(() => {
  fetchRecipes()
})
</script>

<style lang="scss" scoped>
.recipe-manage-container {
  padding: 20px;
  min-height: calc(100vh - 100px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h1 {
    font-size: 1.8rem;
    color: var(--sp-text);
    font-weight: 600;
  }
}

.filter-area {
  margin-bottom: 20px;
  padding: 20px;
  background: var(--sp-surface-muted);
  border-radius: 8px;
}

.table-area {
  .description-cell {
    max-height: 60px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .table-image {
    width: 60px;
    height: 60px;
    border-radius: 6px;

    .image-error {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f7fa;
      color: #909399;
      font-size: 12px;
    }
  }
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--sp-border);
}

.mobile-recipe-list {
  display: none;
}

@media (max-width: 760px) {
  .recipe-manage-container { padding: 12px; }
  .page-header { align-items: stretch; gap: 12px; }
  .page-header h1 { margin: 0; font-size: 1.45rem; }
  .create-btn { width: 100%; margin: 0; }
  .filter-area { padding: 14px; }
  .desktop-recipe-table { display: none; }
  .mobile-recipe-list { display: grid; gap: 12px; }
  .mobile-recipe-list article { padding: 16px; color: var(--sp-text); background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  .mobile-recipe-list header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .mobile-recipe-list header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
  .mobile-recipe-list h2 { margin: 6px 0 0; font-size: 18px; line-height: 1.35; overflow-wrap: anywhere; }
  .mobile-recipe-list p { margin: 12px 0; color: var(--sp-text-secondary); font-size: 13px; line-height: 1.65; }
  .mobile-recipe-list dl { display: grid; gap: 7px; margin: 0 0 14px; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  .mobile-recipe-list dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; font-size: 12px; line-height: 1.5; }
  .mobile-recipe-list dt { color: var(--sp-text-muted); }
  .mobile-recipe-list dd { min-width: 0; margin: 0; color: var(--sp-text-secondary); overflow-wrap: anywhere; }
  .mobile-recipe-list footer { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; padding-top: 12px; border-top: 1px solid var(--sp-border); }
  .mobile-recipe-list footer .el-button { width: 100%; margin: 0; }
}
</style>
