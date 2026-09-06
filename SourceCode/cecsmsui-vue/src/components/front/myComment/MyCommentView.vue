<template>
  <div class="my-comment-container">
    <el-tabs type="border-card" class="demo-tabs">
      <!-- 我的留言选项卡 -->
      <el-tab-pane label="我的留言">
        <el-card class="box-card">
          <template #header>
            <div class="card-header" style="text-align: left">
              <span>我的留言记录</span>
              <div class="header-actions">
                <el-button
                  type="primary"
                  size="small"
                  @click="goToForum"
                >
                  <i class="el-icon-plus"></i>
                  去留言板发表新留言
                </el-button>
              </div>
            </div>
          </template>
          <div>
            <el-table
              :data="myComments"
              style="width: 100%"
              height="430"
              v-loading="loading"
              empty-text="您还没有发表过留言"
            >
              <el-table-column
                label="留言内容"
                prop="content"
                min-width="200"
                show-overflow-tooltip
              >
                <template #default="scope">
                  <div class="content-cell">
                    <p class="content-text">{{ scope.row.content }}</p>
                    <div v-if="scope.row.replies && scope.row.replies.length > 0" class="reply-badge">
                      <i class="el-icon-chat-dot-round"></i>
                      {{ scope.row.replies.length }} 条回复
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="发布时间"
                prop="createTime"
                width="160"
              >
                <template #default="scope">
                  {{ formatTime(scope.row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column
                label="最后更新"
                prop="updateTime"
                width="160"
              >
                <template #default="scope">
                  {{ formatTime(scope.row.updateTime) }}
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                fixed="right"
                width="120"
              >
                <template #default="scope">
                  <el-button
                    size="small"
                    type="primary"
                    @click="viewComment(scope.row)"
                    title="查看详情"
                  >
                    查看
                  </el-button>
                  <el-popconfirm
                    title="确定要删除这条留言吗？删除后不可恢复。"
                    confirm-button-text="确定"
                    cancel-button-text="取消"
                    @confirm="deleteComment(scope.row.id)"
                  >
                    <template #reference>
                      <el-button
                        size="small"
                        type="danger"
                        title="删除留言"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 我的回复选项卡 -->
      <el-tab-pane label="我的回复">
        <el-card class="box-card">
          <template #header>
            <div class="card-header" style="text-align: left">
              <span>我的回复记录</span>
            </div>
          </template>
          <div>
            <el-table
              :data="myReplies"
              style="width: 100%"
              height="430"
              v-loading="loading"
              empty-text="您还没有发表过回复"
            >
              <el-table-column
                label="回复内容"
                prop="content"
                min-width="200"
                show-overflow-tooltip
              >
                <template #default="scope">
                  <div class="content-cell">
                    <p class="content-text">{{ scope.row.content }}</p>
                    <div class="reply-info">
                      回复给：<span class="reply-to">{{ scope.row.replyToUserName || '匿名用户' }}</span>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="原始留言"
                prop="parentContent"
                min-width="150"
                show-overflow-tooltip
              >
                <template #default="scope">
                  <div v-if="scope.row.parentComment">
                    {{ scope.row.parentComment.content.substring(0, 30) }}
                    {{ scope.row.parentComment.content.length > 30 ? '...' : '' }}
                  </div>
                  <div v-else>留言已删除</div>
                </template>
              </el-table-column>
              <el-table-column
                label="发布时间"
                prop="createTime"
                width="160"
              >
                <template #default="scope">
                  {{ formatTime(scope.row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                fixed="right"
                width="120"
              >
                <template #default="scope">
                  <el-button
                    size="small"
                    type="primary"
                    @click="viewParentComment(scope.row)"
                    title="查看原留言"
                  >
                    查看
                  </el-button>
                  <el-popconfirm
                    title="确定要删除这条回复吗？删除后不可恢复。"
                    confirm-button-text="确定"
                    cancel-button-text="取消"
                    @confirm="deleteComment(scope.row.id)"
                  >
                    <template #reference>
                      <el-button
                        size="small"
                        type="danger"
                        title="删除回复"
                      >
                        删除
                      </el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from '@/utils/axios'
import { formatCommentTime as formatTime, sortComments } from '@/utils/comment-time'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const allComments = ref([])
const allReplies = ref([])

// 计算属性
const myComments = computed(() => {
  const currentUser = JSON.parse(sessionStorage.getItem('user') || '{}')
  const userId = currentUser.id

  if (!userId) return []

  // 过滤出当前用户的留言（parent_id 为 null）
  return allComments.value.filter(comment =>
    comment.userId === userId && !comment.parentId
  )
})

const myReplies = computed(() => {
  const currentUser = JSON.parse(sessionStorage.getItem('user') || '{}')
  const userId = currentUser.id

  if (!userId) return []

  // 过滤出当前用户的回复（parent_id 不为 null）
  return allReplies.value.filter(reply =>
    reply.userId === userId && reply.parentId
  )
})

// 方法
// 获取留言列表
const fetchComments = async () => {
  loading.value = true
  try {
    const response = await axios.get('/comment/list')

    if (response.data && response.data.code === 200) {
      let commentsData = []

      if (response.data.result && Array.isArray(response.data.result)) {
        commentsData = response.data.result
      } else if (response.data.data && Array.isArray(response.data.data)) {
        commentsData = response.data.data
      } else if (Array.isArray(response.data)) {
        commentsData = response.data
      }

      // 分离留言和回复
      allComments.value = sortComments(commentsData.filter(comment => !comment.parentId))
      // /comment/list nests replies under their parent; include those records
      // before applying the current user's filter and chronological ordering.
      const replies = commentsData.flatMap(comment => comment.parentId ? [comment] : comment.replies || [])
      allReplies.value = sortComments([...new Map(replies.map(reply => [reply.id, reply])).values()])


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

// 删除留言/回复
const deleteComment = async (commentId) => {
  try {
    const response = await axios.delete(`/comment/delete/${commentId}`)

    if (response.data && response.data.code === 200) {
      ElMessage.success('删除成功')
      await fetchComments() // 重新加载数据
    } else {
      ElMessage.error(response.data?.msg || '删除失败')
    }
  } catch (err) {
    console.error('删除失败:', err)
    ElMessage.error('删除失败: ' + (err.message || '未知错误'))
  }
}

// 查看留言详情
const viewComment = (comment) => {
  // 跳转到留言板页面，并定位到该留言
  router.push({
    path: '/front/forum/ForumHomeView',
    query: { commentId: comment.id }
  })
}

// 查看原留言
const viewParentComment = (reply) => {
  if (reply.parentId) {
    // 跳转到留言板页面，并定位到父留言
    router.push({
      path: '/front/forum/ForumHomeView',
      query: { commentId: reply.parentId }
    })
  }
}

// 跳转到留言板
const goToForum = () => {
  router.push('/front/forum/ForumHomeView')
}

// 生命周期
onMounted(() => {
  fetchComments()
})
</script>

<style scoped>
.my-comment-container {
  padding: 20px;
  height: 85vh;
}

.demo-tabs {
  height: 100%;
}

.box-card {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 0 10px 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.content-cell {
  padding: 5px 0;
}

.content-text {
  margin: 0 0 8px 0;
  line-height: 1.5;
  color: #303133;
  font-size: 14px;
}

.reply-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background-color: #f0f9ff;
  border-radius: 12px;
  color: #409eff;
  font-size: 12px;
  font-weight: 500;
}

.reply-info {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.reply-to {
  color: #409eff;
  font-weight: 500;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .my-comment-container {
    padding: 10px;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
