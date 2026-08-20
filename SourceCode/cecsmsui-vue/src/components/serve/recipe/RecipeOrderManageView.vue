<template>
  <div class="order-manage-container">
    <div class="page-header">
      <h1>菜谱预订管理</h1>
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
        class="desktop-order-table"
        v-loading="loading"
        :data="orderList"
        style="width: 100%"
        border
        stripe
      >
        <el-table-column label="ID" prop="id" width="80" />

        <el-table-column label="用户" prop="userName" width="120" />

        <el-table-column label="菜谱" prop="recipeName" min-width="150" />

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

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="success"
              size="small"
              @click="handleComplete(row)"
              :loading="completingOrderId === row.id"
            >
              完成
            </el-button>
            <span v-else-if="row.status === 1" class="status-text">已完成</span>
            <el-button
              v-else
              type="info"
              size="small"
              disabled
            >
              已取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-loading="loading" class="mobile-order-list" aria-label="菜谱预订管理列表">
        <article v-for="order in orderList" :key="order.id">
          <header>
            <div>
              <small>ORDER {{ order.id }}</small>
              <h2>{{ order.recipeName || '未命名菜谱' }}</h2>
            </div>
            <el-tag :type="getStatusType(order.status)">{{ getStatusText(order.status) }}</el-tag>
          </header>
          <dl>
            <div><dt>预订用户</dt><dd>{{ order.userName || '--' }}</dd></div>
            <div><dt>预订时间</dt><dd>{{ formatTime(order.orderTime) || '--' }}</dd></div>
            <div><dt>完成时间</dt><dd>{{ order.completeTime ? formatTime(order.completeTime) : '--' }}</dd></div>
            <div><dt>备注</dt><dd>{{ order.remark || '无' }}</dd></div>
          </dl>
          <el-button
            v-if="order.status === 0"
            type="success"
            :loading="completingOrderId === order.id"
            @click="handleComplete(order)"
          >标记完成</el-button>
          <div v-else class="completed-hint">{{ order.status === 1 ? '该预订已完成' : '该预订已取消' }}</div>
        </article>
        <el-empty v-if="!loading && !orderList.length" description="暂无预订数据" :image-size="64" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getAllOrders, updateOrderStatus } from '@/api/recipe'

// 响应式数据
const filterForm = reactive({
  recipeName: '',
  status: ''
})
const orderList = ref([])
const loading = ref(false)
const completingOrderId = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 获取所有预订列表
const fetchAllOrders = async () => {
  try {
    loading.value = true
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      recipeName: filterForm.recipeName,
      ...(filterForm.status === '' ? {} : { status: filterForm.status })
    }

    const { data: res } = await getAllOrders(params)

    if (res && res.code === 200) {
      orderList.value = res.result?.records || []
      total.value = res.result?.total || 0
    } else {
      const errorMsg = res?.msg || '获取预订列表失败'
      ElMessage.error(errorMsg)
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchAllOrders()
}

// 重置筛选
const resetFilter = () => {
  filterForm.recipeName = ''
  filterForm.status = ''
  currentPage.value = 1
  fetchAllOrders()
}

// 完成预订
const handleComplete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要将 "${row.userName}" 的 "${row.recipeName}" 预订标记为已完成吗？`,
      '确认完成',
      {
        confirmButtonText: '确定完成',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    completingOrderId.value = row.id

    const { data: res } = await updateOrderStatus({ id: row.id, status: 1 })

    if (res && res.code === 200) {
      ElMessage.success('操作成功')
      fetchAllOrders()
    } else {
      const errorMsg = res?.msg || '操作失败'
      ElMessage.error(errorMsg)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败，请重试')
    }
  } finally {
    completingOrderId.value = null
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
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  fetchAllOrders()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchAllOrders()
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
  fetchAllOrders()
})
</script>

<style lang="scss" scoped>
.order-manage-container {
  padding: 20px;
  min-height: calc(100vh - 100px);
}

.page-header {
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
  .status-text {
    color: #67c23a;
    font-weight: 500;
  }
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--sp-border);
}

.mobile-order-list { display: none; }

@media (max-width: 760px) {
  .order-manage-container { padding: 12px; }
  .page-header h1 { margin: 0; font-size: 1.45rem; }
  .filter-area { padding: 14px; }
  .desktop-order-table { display: none; }
  .mobile-order-list { display: grid; gap: 12px; }
  .mobile-order-list article { padding: 16px; color: var(--sp-text); background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  .mobile-order-list header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .mobile-order-list header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
  .mobile-order-list h2 { margin: 6px 0 0; font-size: 18px; line-height: 1.35; overflow-wrap: anywhere; }
  .mobile-order-list dl { display: grid; gap: 7px; margin: 14px 0; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  .mobile-order-list dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; font-size: 12px; line-height: 1.5; }
  .mobile-order-list dt { color: var(--sp-text-muted); }
  .mobile-order-list dd { min-width: 0; margin: 0; color: var(--sp-text-secondary); overflow-wrap: anywhere; }
  .mobile-order-list .el-button { width: 100%; margin: 0; }
  .completed-hint { padding: 10px 12px; color: var(--sp-text-secondary); text-align: center; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
}
</style>
