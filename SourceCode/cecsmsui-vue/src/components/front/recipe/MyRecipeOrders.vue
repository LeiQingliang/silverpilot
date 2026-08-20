<template>
  <div class="my-recipe-orders">
    <div class="page-header">
      <h1>我的菜谱预订</h1>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-area">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="菜谱名称">
          <el-input
            v-model="filterForm.recipeName"
            placeholder="搜索菜谱名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filterForm.status"
            placeholder="全部状态"
            clearable
          >
            <el-option label="全部" value="" />
            <el-option label="已预订" :value="0" />
            <el-option label="已完成" :value="1" />
            <el-option label="已取消" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch" :icon="Search">搜索</el-button>
          <el-button @click="resetFilter" :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 预订列表 -->
    <div class="orders-table">
      <el-table
        class="desktop-orders-table"
        v-loading="loading"
        :data="orderList"
        style="width: 100%"
        border
        stripe
      >
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="菜谱名称" prop="recipeName" min-width="150" />
        <el-table-column label="预订时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.orderTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="{ row }">
            {{ row.completeTime ? formatTime(row.completeTime) : '--' }}
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="150">
          <template #default="{ row }">
            {{ row.remark || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="danger"
              size="small"
              @click="cancelOrder(row)"
              :loading="cancelingOrderId === row.id"
            >
              取消
            </el-button>
            <span v-else class="status-text">{{ getStatusText(row.status) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-loading="loading" class="mobile-orders-list" aria-label="菜谱预订列表">
        <article v-for="row in orderList" :key="row.id">
          <header><div><small>用餐预订 #{{ row.id }}</small><h2>{{ row.recipeName }}</h2></div><el-tag :type="getStatusType(row.status)" effect="plain">{{ getStatusText(row.status) }}</el-tag></header>
          <dl>
            <div><dt>预订时间</dt><dd>{{ formatTime(row.orderTime) }}</dd></div>
            <div><dt>完成时间</dt><dd>{{ row.completeTime ? formatTime(row.completeTime) : '尚未完成' }}</dd></div>
            <div><dt>备注</dt><dd>{{ row.remark || '无特殊备注' }}</dd></div>
          </dl>
          <footer v-if="row.status === 0"><el-button type="danger" plain :loading="cancelingOrderId === row.id" @click="cancelOrder(row)">取消预订</el-button></footer>
        </article>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > pageSize">
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

    <!-- 空状态 -->
    <div v-if="orderList.length === 0 && !loading" class="empty-state">
      <el-empty description="暂无预订记录" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import api from '@/utils/axios'

// 响应式数据
const filterForm = reactive({
  recipeName: '',
  status: ''
})
const orderList = ref([])
const loading = ref(false)
const cancelingOrderId = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 获取我的预订
const fetchMyOrders = async () => {
  try {
    loading.value = true
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      recipeName: filterForm.recipeName,
      ...(filterForm.status === '' ? {} : { status: filterForm.status })
    }
    const { data } = await api.get('/recipe-order/my-orders', { params })

    if (data.code === 200) {
      orderList.value = data.result?.records || []
      total.value = data.result?.total || 0
    } else {
      ElMessage.error(data.msg || '获取预订列表失败')
    }
  } catch (error) {
    if (error.response?.status !== 401) {
      ElMessage.error(error.response?.data?.msg || '获取预订列表失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchMyOrders()
}

// 重置筛选
const resetFilter = () => {
  filterForm.recipeName = ''
  filterForm.status = ''
  currentPage.value = 1
  fetchMyOrders()
}

// 取消预订
const cancelOrder = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消"${row.recipeName}"的预订吗？`,
      '确认取消',
      {
        confirmButtonText: '确定取消',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    cancelingOrderId.value = row.id

    const { data } = await api.post(`/recipe-order/cancel/${row.id}`)

    if (data.code === 200) {
      ElMessage.success('取消成功')
      fetchMyOrders()
    } else {
      ElMessage.error(data.msg || '取消失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.msg || '取消失败，请重试')
    }
  } finally {
    cancelingOrderId.value = null
  }
}

// 获取状态文本
const getStatusText = (status) => {
  const statusMap = {
    0: '已预订',
    1: '已完成',
    2: '已取消'
  }
  return statusMap[status] || '未知'
}

// 获取状态类型
const getStatusType = (status) => {
  const typeMap = {
    0: 'primary',
    1: 'success',
    2: 'info'
  }
  return typeMap[status] || 'default'
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchMyOrders()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchMyOrders()
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  try {
    const date = new Date(time)
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch (e) {
    return '时间格式错误'
  }
}

// 生命周期
onMounted(() => {
  fetchMyOrders()
})
</script>

<style scoped>
.my-recipe-orders {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
  text-align: center;
}

.page-header h1 {
  font-size: 24px;
  color: var(--sp-text);
  font-weight: 600;
}

.filter-area {
  margin-bottom: 20px;
  padding: 20px;
  background-color: var(--sp-surface-muted);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-md);
}

.orders-table {
  margin-bottom: 20px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--sp-border);
}

.empty-state {
  margin-top: 100px;
  text-align: center;
}

.status-text {
  color: var(--sp-text-muted);
  font-size: 13px;
}
.mobile-orders-list { display: none; }

@media (max-width: 760px) {
  .my-recipe-orders { padding: 18px 12px 36px; }
  .page-header { text-align: left; }
  .filter-area { padding: 14px; }
  .filter-area :deep(.el-form) { display: grid; }
  .filter-area :deep(.el-form-item) { width: 100%; margin-right: 0; }
  .filter-area :deep(.el-input), .filter-area :deep(.el-select) { width: 100%; }
  .desktop-orders-table { display: none; }
  .mobile-orders-list { display: grid; gap: 12px; }
  .mobile-orders-list article { padding: 16px; background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  .mobile-orders-list header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
  .mobile-orders-list header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
  .mobile-orders-list h2 { margin: 5px 0 0; color: var(--sp-text); font-size: 16px; }
  .mobile-orders-list dl { display: grid; gap: 8px; margin: 14px 0 0; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  .mobile-orders-list dl div { display: grid; grid-template-columns: 68px minmax(0, 1fr); gap: 8px; color: var(--sp-text-secondary); font-size: 12px; line-height: 1.5; }
  .mobile-orders-list dt { color: var(--sp-text-muted); }.mobile-orders-list dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
  .mobile-orders-list footer { display: flex; justify-content: flex-end; margin-top: 12px; }
  .mobile-orders-list footer :deep(.el-button) { min-height: 40px; }
  .pagination { overflow-x: auto; justify-content: flex-start; }
}
</style>
