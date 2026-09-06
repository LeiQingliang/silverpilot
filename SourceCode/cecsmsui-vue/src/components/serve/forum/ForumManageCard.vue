<template>
  <el-card class="box-card">
    <template #header>
      <div class="card-header">
        <span>留言管理</span>
        <div style="display: flex">
          <el-input
            v-model="searchKeyword"
            placeholder="请输入留言内容、用户名或用户电话"
            class="input-with-select"
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch" :icon="Search" />
            </template>
          </el-input>
          <el-select
            v-model="filterType"
            placeholder="筛选类型"
            class="filter-select"
            style="width: 120px; margin-left: 10px"
            @change="handleFilterChange"
          >
            <el-option label="全部留言" value="all" />
            <el-option label="主留言" value="parent" />
            <el-option label="回复" value="reply" />
            <el-option label="已删除" value="deleted" />
          </el-select>
          <el-button
            style="margin-left: 10px"
            type="success"
            :icon="Refresh"
            @click="refreshData"
          >
            刷新
          </el-button>
        </div>
      </div>
    </template>

    <!-- 表格组件 -->
    <el-table
      :data="filteredComments"
      :default-sort="{ prop: 'createTime', order: 'descending' }"
      style="width: 100%"
      height="450"
      v-loading="loading"
      empty-text="暂无留言数据"
      :row-class-name="tableRowClassName"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="80"
        sortable
      />
      <el-table-column
        label="留言内容"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="scope">
          <div class="content-cell">
            <div class="content-preview">
              {{ scope.row.content?.substring(0, 50) }}
              {{ scope.row.content?.length > 50 ? '...' : '' }}
            </div>
            <div v-if="scope.row.isDeleted === 1" class="deleted-badge">
              已删除
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="用户信息"
        width="180"
      >
        <template #default="scope">
          <div class="user-info">
            <div class="user-name">{{ scope.row.userName || '匿名用户' }}</div>
            <div class="user-telephone">{{ scope.row.userTelephone || '-' }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="类型"
        width="100"
      >
        <template #default="scope">
          <el-tag
            :type="getCommentTypeTag(scope.row)"
            size="small"
          >
            {{ getCommentTypeText(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createTime"
        label="发布时间"
        width="180"
        sortable
        :sort-method="(a, b) => compareCommentTimes(a, b, true)"
      >
        <template #default="scope">
          {{ formatTime(scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        fixed="right"
        width="150"
      >
        <template #default="scope">
          <el-button
            size="small"
            type="primary"
            @click="viewDetail(scope.row)"
          >
            查看详情
          </el-button>
          <el-popconfirm
            v-if="scope.row.isDeleted !== 1"
            title="确定要删除这条留言吗？此操作不可恢复。"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="deleteComment(scope.row.id)"
          >
            <template #reference>
              <el-button
                size="small"
                type="danger"
              >
                删除
              </el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm
            v-else
            title="确定要恢复这条留言吗？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="restoreComment(scope.row.id)"
          >
            <template #reference>
              <el-button
                size="small"
                type="warning"
              >
                恢复
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
  <CommentDetailDialog
    v-model="detailVisible"
    :comment="selectedComment"
  />
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from '@/utils/axios'
import { compareCommentTimes, formatCommentDateTime as formatTime } from '@/utils/comment-time'
import CommentDetailDialog from './CommentDetailDialog.vue'

// 响应式数据
const comments = ref([])
const filteredComments = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterType = ref('all')
const detailVisible = ref(false)
const selectedComment = ref(null)

// 计算属性：根据搜索关键词和筛选类型过滤留言
const computedFilteredComments = computed(() => {
  let result = comments.value

  // 根据筛选类型过滤
  if (filterType.value === 'parent') {
    result = result.filter(comment => !comment.parentId)
  } else if (filterType.value === 'reply') {
    result = result.filter(comment => comment.parentId)
  } else if (filterType.value === 'deleted') {
    result = result.filter(comment => comment.isDeleted === 1)
  } else {
    // 默认显示未删除的留言
    result = result.filter(comment => comment.isDeleted !== 1)
  }

  // 根据搜索关键词过滤
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase().trim()
    result = result.filter(comment => {
      return (
        (comment.content && comment.content.toLowerCase().includes(keyword)) ||
        (comment.userName && comment.userName.toLowerCase().includes(keyword)) ||
        (comment.userTelephone && comment.userTelephone.includes(keyword))
      )
    })
  }

  return result
})

// 监听过滤条件变化
watch([searchKeyword, filterType, comments], () => {
  filteredComments.value = computedFilteredComments.value
}, { immediate: true })

// 方法
const fetchComments = async () => {
  loading.value = true
  try {
    const response = await axios.get('/comment/manage')

    if (response.data && response.data.code === 200) {
      let commentsData = []

      if (response.data.result && Array.isArray(response.data.result)) {
        commentsData = response.data.result
      } else if (response.data.data && Array.isArray(response.data.data)) {
        commentsData = response.data.data
      } else if (Array.isArray(response.data)) {
        commentsData = response.data
      }

      comments.value = commentsData


    } else {
      ElMessage.error('获取留言列表失败: ' + (response.data?.msg || '未知错误'))
    }
  } catch (err) {
    console.error('获取留言列表失败:', err)
    ElMessage.error('获取留言列表失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  fetchComments()
}

const handleFilterChange = () => {
  fetchComments()
}

const refreshData = () => {
  searchKeyword.value = ''
  filterType.value = 'all'
  fetchComments()
}

const deleteComment = async (commentId) => {
  try {
    const response = await axios.delete(`/comment/delete/${commentId}`)
    if (response.data && response.data.code === 200) {
      ElMessage.success('删除成功')
      // 从当前列表中移除已删除的项
      const index = comments.value.findIndex(comment => comment.id === commentId)
      if (index !== -1) {
        comments.value.splice(index, 1)
      }
      // 重新获取数据以确保同步
      await fetchComments()
    } else {
      ElMessage.error(response.data?.msg || '删除失败')
    }
  } catch (err) {
    console.error('删除失败:', err)
    ElMessage.error('删除失败: ' + (err.message || '未知错误'))
  }
}

// 新增恢复留言功能
const restoreComment = async (commentId) => {
  try {
    const response = await axios.post(`/comment/restore/${commentId}`)
    if (response.data && response.data.code === 200) {
      ElMessage.success('恢复成功')
      await fetchComments() // 重新获取数据
    } else {
      ElMessage.error(response.data?.msg || '恢复失败')
    }
  } catch (err) {
    console.error('恢复失败:', err)
    ElMessage.error('恢复失败: ' + (err.message || '未知错误'))
  }
}

const viewDetail = (comment) => {
  selectedComment.value = comment
  detailVisible.value = true
}

const getCommentTypeTag = (comment) => {
  if (comment.isDeleted === 1) return 'danger'
  if (comment.parentId) return 'warning'
  return 'primary'
}

const getCommentTypeText = (comment) => {
  if (comment.isDeleted === 1) return '已删除'
  if (comment.parentId) return '回复'
  return '主留言'
}

const tableRowClassName = ({ row }) => {
  if (row.isDeleted === 1) {
    return 'deleted-row'
  }
  return ''
}

// 生命周期
onMounted(() => {
  fetchComments()
})
</script>

<style scoped>
.box-card {
  width: 99%;
  height: 94%;
  margin-top: 12px;
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.input-with-select {
  width: 300px;
}

.filter-select {
  margin-left: 10px;
}

.content-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.content-preview {
  line-height: 1.4;
  color: #303133;
}

.deleted-badge {
  display: inline-block;
  padding: 2px 6px;
  background-color: #fef0f0;
  color: #f56c6c;
  border-radius: 4px;
  font-size: 12px;
  width: fit-content;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 500;
  color: #303133;
}

.user-telephone {
  font-size: 12px;
  color: #909399;
}

/* 已删除行的样式 */
:deep(.deleted-row) {
  background-color: #fafafa;
  color: #c0c4cc;
}

:deep(.deleted-row .content-preview) {
  color: #c0c4cc;
  text-decoration: line-through;
}

:deep(.deleted-row .user-name) {
  color: #c0c4cc;
}
</style>
