<template>
  <div class="forum-home-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">社区留言板</h1>
        <p class="page-subtitle">与邻居分享生活，交流经验，共建和谐社区</p>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="main-content">
      <!-- 左侧留言列表 -->
      <div class="left-column">
        <!-- 发布留言卡片 -->
        <div class="post-card">
          <el-card shadow="never" class="post-card-inner">
            <template #header>
              <div class="post-header">
                <span>📝 发布新留言</span>
              </div>
            </template>
            <el-input
              v-model="newComment"
              type="textarea"
              :rows="4"
              placeholder="有什么想和大家分享的吗？"
              maxlength="500"
              show-word-limit
              class="post-textarea"
            />
            <div class="post-actions">
              <el-button
                type="primary"
                @click="handleSubmitComment"
                :loading="submitting"
                :disabled="!newComment.trim()"
                round
              >
                发表留言
              </el-button>
              <el-button @click="newComment = ''" :disabled="!newComment.trim()" round>清空</el-button>
            </div>
          </el-card>
        </div>

        <!-- 留言列表区域 -->
        <div class="comments-section">
          <div class="section-header">
            <h2>最新留言</h2>
            <el-select
              v-model="sortBy"
              placeholder="排序方式"
              size="small"
              class="sort-select"
              @change="handleSortChange"
              popper-class="sort-popper"
            >
              <el-option label="最新发布" value="newest" />
              <el-option label="最多回复" value="most_replies" />
              <el-option label="最早发布" value="oldest" />
            </el-select>
          </div>

          <!-- 加载骨架屏 -->
          <div v-if="loading && comments.length === 0" class="loading-container">
            <el-skeleton :rows="3" animated />
            <el-skeleton :rows="3" animated class="mt-3" />
          </div>

          <!-- 空状态 -->
          <div v-else-if="comments.length === 0" class="empty-state">
            <el-empty description="暂无留言，快来发表第一条留言吧！" />
          </div>

          <!-- 留言列表 -->
          <div v-else class="comments-list">
            <div
              v-for="comment in comments"
              :key="comment.id"
              class="comment-item"
              :class="{ 'comment-item-active': activeCommentId === comment.id }"
            >
              <el-card
                shadow="hover"
                class="comment-card"
                role="group"
                tabindex="0"
                :aria-label="`查看 ${comment.userName || '匿名用户'} 的留言与回复`"
                @click="selectComment(comment)"
                @keydown.enter="selectComment(comment)"
              >
                <!-- 留言头部 -->
                <div class="comment-header">
                  <div class="user-info">
                    <el-avatar :size="44" :src="getAvatarUrl(comment.userAvatar)" class="user-avatar">
                      {{ comment.userName ? comment.userName.charAt(0) : '?' }}
                    </el-avatar>
                    <div class="user-details">
                      <span class="user-name">{{ comment.userName || '匿名用户' }}</span>
                      <div class="comment-meta">
                        <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
                        <span v-if="comment.replies && comment.replies.length > 0" class="reply-count">
                          <i class="el-icon-chat-dot-round"></i>
                          {{ comment.replies.length }} 条回复
                        </span>
                      </div>
                    </div>
                  </div>
                  <div class="comment-actions">
                    <el-button
                      v-if="canDeleteComment(comment)"
                      type="danger"
                      size="small"
                      @click.stop="handleDeleteComment(comment.id)"
                      :loading="deletingId === comment.id"
                      plain
                      round
                    >
                      删除
                    </el-button>
                  </div>
                </div>

                <!-- 留言内容 -->
                <div class="comment-content">
                  <p class="comment-text">{{ comment.content }}</p>
                </div>

                <!-- 留言底部 -->
                <div class="comment-footer">
                  <el-button
                    type="primary"
                    size="small"
                    plain
                    round
                    @click.stop="focusReplyInput(comment)"
                  >
                    <i class="el-icon-chat-line-round"></i>
                    回复
                  </el-button>
                </div>
              </el-card>

              <!-- 回复列表 -->
              <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
                <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                  <div class="reply-content">
                    <div class="reply-header">
                      <el-avatar :size="32" :src="getAvatarUrl(reply.userAvatar)" class="reply-avatar">
                        {{ reply.userName ? reply.userName.charAt(0) : '?' }}
                      </el-avatar>
                      <div class="reply-user-info">
                        <span class="reply-user-name">{{ reply.userName || '匿名用户' }}</span>
                        <span class="reply-to-text">回复</span>
                        <span class="reply-target">@{{ reply.replyToUserName || '匿名用户' }}</span>
                      </div>
                      <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
                    </div>
                    <p class="reply-text">{{ reply.content }}</p>
                  </div>
                  <div class="reply-actions">
                    <el-button
                      v-if="canDeleteComment(reply)"
                      type="danger"
                      size="mini"
                      @click.stop="handleDeleteComment(reply.id)"
                      :loading="deletingId === reply.id"
                      plain
                      round
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 加载更多 -->
          <div v-if="comments.length > 0 && !noMoreData" class="load-more">
            <el-button
              link
              @click="loadMore"
              :loading="loading"
              :disabled="loading"
              class="load-more-btn"
            >
              加载更多留言
            </el-button>
          </div>
        </div>
      </div>

      <!-- 右侧回复面板（悬浮） -->
      <div v-if="activeComment" class="right-column">
        <el-card shadow="never" class="reply-panel">
          <template #header>
            <div class="reply-panel-header">
              <div class="reply-to-user">
                <el-avatar :size="40" :src="getAvatarUrl(activeComment.userAvatar)">
                  {{ activeComment.userName ? activeComment.userName.charAt(0) : '?' }}
                </el-avatar>
                <div>
                  <h4>回复给：{{ activeComment.userName }}</h4>
                  <p class="original-content">“{{ activeComment.content.substring(0, 60) }}{{ activeComment.content.length > 60 ? '...' : '' }}”</p>
                </div>
              </div>
              <el-button
                link
                icon="el-icon-close"
                @click="clearActiveComment"
                class="close-panel-btn"
              />
            </div>
          </template>

          <!-- 回复输入区 -->
          <div class="reply-input-area">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="4"
              :placeholder="`回复 ${activeComment.userName}：`"
              maxlength="300"
              show-word-limit
              class="reply-textarea"
              resize="none"
            />
            <div class="reply-submit-actions">
              <el-button
                type="primary"
                @click="handleSubmitReply"
                :loading="submittingReply"
                :disabled="!replyContent.trim()"
                round
              >
                发送回复
              </el-button>
              <el-button @click="clearReply" round>取消</el-button>
            </div>
          </div>

          <!-- 已有回复列表 -->
          <div v-if="activeComment.replies && activeComment.replies.length > 0" class="existing-replies">
            <h4>已有回复 · {{ activeComment.replies.length }}</h4>
            <div class="replies-scroll">
              <div v-for="reply in activeComment.replies" :key="reply.id" class="existing-reply-item">
                <div class="reply-user">
                  <el-avatar :size="28" :src="getAvatarUrl(reply.userAvatar)">
                    {{ reply.userName ? reply.userName.charAt(0) : '?' }}
                  </el-avatar>
                  <span class="reply-user-name">{{ reply.userName }}</span>
                </div>
                <p class="reply-content-preview">{{ reply.content }}</p>
                <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from '@/utils/axios'

// ------ 响应式数据 ------
const newComment = ref('')
const replyContent = ref('')
const comments = ref([])
const activeComment = ref(null)
const activeCommentId = ref(null)
const loading = ref(false)
const submitting = ref(false)
const submittingReply = ref(false)
const deletingId = ref(null)
const currentPage = ref(1)
const total = ref(0)
const sortBy = ref('newest')

// 是否无更多数据
const noMoreData = computed(() => {
  return comments.value.length >= total.value
})

// ------ 辅助方法 ------
const getAvatarUrl = (avatar) => avatar || ''

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

const isAdmin = () => {
  const currentUser = JSON.parse(sessionStorage.getItem('user') || '{}')
  return currentUser.roleId === 1
}

const canDeleteComment = (comment) => {
  const commentUserId = comment.userId || comment.user_id
  const currentUser = JSON.parse(sessionStorage.getItem('user') || '{}')
  const currentUserId = currentUser.id
  return commentUserId === currentUserId || isAdmin()
}

// ------ 数据获取 ------
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
      comments.value = JSON.parse(JSON.stringify(commentsData))
      total.value = comments.value.length

      if (activeCommentId.value) {
        const activeCommentData = comments.value.find(c => c.id === activeCommentId.value)
        if (activeCommentData) {
          activeComment.value = JSON.parse(JSON.stringify(activeCommentData))
        }
      }
    } else {
      throw new Error(response.data?.msg || '获取留言列表失败')
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('获取留言列表失败')
  } finally {
    loading.value = false
  }
}

// 发表留言
const handleSubmitComment = async () => {
  if (!newComment.value.trim()) {
    ElMessage.warning('请输入留言内容')
    return
  }
  submitting.value = true
  try {
    const response = await axios.post('/comment/add', {
      content: newComment.value.trim(),
      parentId: null,
      replyTo: null
    })
    if (response.data && response.data.code === 200) {
      ElMessage.success('留言发表成功')
      newComment.value = ''
      await fetchComments()
    } else {
      ElMessage.error(response.data?.msg || '留言发表失败')
    }
  } catch (err) {
    ElMessage.error('留言发表失败')
  } finally {
    submitting.value = false
  }
}

// 选中留言
const selectComment = (comment) => {
  if (!comment.userId) {
    ElMessage.error('该留言的用户信息不完整，请刷新页面后重试')
    return
  }
  activeCommentId.value = comment.id
  activeComment.value = JSON.parse(JSON.stringify(comment))
  replyContent.value = ''
  nextTick(() => {
    const rightColumn = document.querySelector('.right-column')
    if (rightColumn) {
      rightColumn.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  })
}

// 聚焦回复输入框
const focusReplyInput = (comment) => {
  selectComment(comment)
  nextTick(() => {
    const textarea = document.querySelector('.reply-textarea textarea')
    if (textarea) textarea.focus()
  })
}

// 回复留言
const handleSubmitReply = async () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  if (!activeComment.value) {
    ElMessage.warning('请先选择要回复的留言')
    return
  }
  const replyToUserId = activeComment.value.userId
  if (!replyToUserId) {
    ElMessage.error('无法获取被回复用户信息，请刷新页面后重试')
    return
  }
  const currentUser = JSON.parse(sessionStorage.getItem('user') || '{}')
  if (!currentUser.id) {
    ElMessage.error('用户未登录，请重新登录')
    return
  }
  submittingReply.value = true
  try {
    const response = await axios.post('/comment/reply', {
      content: replyContent.value.trim(),
      parentId: activeComment.value.id,
      replyTo: replyToUserId
    })
    if (response.data && response.data.code === 200) {
      ElMessage.success('回复成功')
      replyContent.value = ''
      await fetchComments()
      clearActiveComment()
    } else {
      ElMessage.error(response.data?.msg || '回复失败')
    }
  } catch (err) {
    ElMessage.error('回复失败')
  } finally {
    submittingReply.value = false
  }
}

// 删除留言
const handleDeleteComment = async (commentId) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条留言吗？删除后不可恢复。',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    deletingId.value = commentId
    const response = await axios.delete(`/comment/delete/${commentId}`)
    if (response.data && response.data.code === 200) {
      ElMessage.success('删除成功')
      if (commentId === activeCommentId.value) clearActiveComment()
      await fetchComments()
    } else {
      ElMessage.error(response.data?.msg || '删除失败')
    }
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('删除失败')
  } finally {
    deletingId.value = null
  }
}

// 清除选中留言
const clearActiveComment = () => {
  activeComment.value = null
  activeCommentId.value = null
  replyContent.value = ''
}

// 清空回复内容
const clearReply = () => {
  replyContent.value = ''
}

// 排序
const handleSortChange = () => {
  if (sortBy.value === 'newest') {
    comments.value.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
  } else if (sortBy.value === 'oldest') {
    comments.value.sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
  } else if (sortBy.value === 'most_replies') {
    comments.value.sort((a, b) => {
      const aReplies = a.replies ? a.replies.length : 0
      const bReplies = b.replies ? b.replies.length : 0
      return bReplies - aReplies
    })
  }
  comments.value = [...comments.value]
}

// 加载更多 (单页模式实际就是重新获取所有，这里保留方法)
const loadMore = () => {
  currentPage.value++
  fetchComments()
}

// 生命周期
onMounted(() => {
  const user = JSON.parse(sessionStorage.getItem('user') || '{}')
  const token = sessionStorage.getItem('token') || localStorage.getItem('token')
  if (!user.id || !token) {
    ElMessage.warning('请先登录')
    window.location.href = '/login'
    return
  }
  fetchComments()
})
</script>

<style scoped lang="scss">
// 配色变量
$primary: var(--sp-brand);
$primary-light: var(--sp-brand-soft);
$text-primary: var(--sp-text);
$text-secondary: var(--sp-text-secondary);
$border-light: var(--sp-border);
$bg-card: var(--sp-surface);
$bg-page: var(--sp-bg);
$shadow-sm: var(--sp-shadow-xs);
$shadow-md: var(--sp-shadow-sm);
$shadow-lg: var(--sp-shadow-md);

.forum-home-container {
  min-height: calc(100vh - var(--sp-header-height));
  background: $bg-page;
  padding: 24px 20px 40px;
}

/* 页面头部 */
.page-header {
  max-width: 1280px;
  margin: 0 auto 32px;
  background: linear-gradient(135deg, var(--sp-surface) 0%, var(--sp-surface-muted) 100%);
  border-radius: 32px;
  padding: 32px 40px;
  box-shadow: $shadow-md;
  border: 1px solid $border-light;
}

.header-content {
  .page-title {
    font-size: 28px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 8px 0;
    letter-spacing: -0.3px;
  }
  .page-subtitle {
    font-size: 15px;
    color: $text-secondary;
    margin: 0;
  }
}

/* 主内容区 */
.main-content {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  gap: 28px;
}

.left-column {
  flex: 1;
  min-width: 0;
}

.right-column {
  width: 380px;
  position: sticky;
  top: 88px;
  height: calc(100vh - 100px);
  overflow-y: auto;
}

/* 发布卡片 */
.post-card {
  margin-bottom: 28px;
}

.post-card-inner {
  border-radius: 24px;
  border: 1px solid $border-light;
  background: $bg-card;
  box-shadow: $shadow-sm;

  :deep(.el-card__header) {
    padding: 18px 24px;
    border-bottom: 1px solid $border-light;
  }
  :deep(.el-card__body) {
    padding: 24px;
  }
}

.post-header {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
}

.post-textarea {
  margin-bottom: 20px;
  :deep(.el-textarea__inner) {
    border-radius: 16px;
    border-color: $border-light;
    transition: all 0.2s;
    &:focus {
      border-color: $primary;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }
  }
}

.post-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  .el-button {
    border-radius: 40px;
    padding: 8px 20px;
  }
}

/* 留言列表区域 */
.comments-section {
  background: $bg-card;
  border-radius: 24px;
  border: 1px solid $border-light;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid $border-light;
  background: $bg-card;

  h2 {
    font-size: 20px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
  }
  .sort-select {
    width: 120px;
    :deep(.el-input__wrapper) {
      border-radius: 30px;
    }
  }
}

.comments-list {
  .comment-item {
    border-bottom: 1px solid $border-light;
    transition: background 0.2s;
    &:hover {
      background: $primary-light;
    }
    &.comment-item-active {
      background: $primary-light;
      border-left: 3px solid $primary;
    }
  }
}

.comment-card {
  border: none;
  border-radius: 0;
  cursor: pointer;
  background: transparent;
  :deep(.el-card__body) {
    padding: 20px 24px;
  }
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.user-info {
  display: flex;
  gap: 14px;
  .user-avatar {
    flex-shrink: 0;
  }
  .user-details {
    .user-name {
      font-weight: 600;
      color: $text-primary;
      font-size: 16px;
      margin-bottom: 4px;
      display: inline-block;
    }
    .comment-meta {
      display: flex;
      gap: 12px;
      font-size: 12px;
      color: $text-secondary;
      .reply-count {
        display: flex;
        align-items: center;
        gap: 4px;
        color: $primary;
      }
    }
  }
}

.comment-content {
  margin-bottom: 16px;
  .comment-text {
    font-size: 15px;
    line-height: 1.6;
    color: $text-primary;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.comment-footer {
  display: flex;
  justify-content: flex-end;
  .el-button {
    border-radius: 30px;
  }
}

.replies-list {
  background: var(--sp-surface-muted);
  padding: 0 24px 16px 24px;
  border-top: 1px solid $border-light;
}

.reply-item {
  display: flex;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid $border-light;
  &:last-child {
    border-bottom: none;
  }
  .reply-content {
    flex: 1;
    .reply-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 8px;
      .reply-avatar {
        flex-shrink: 0;
      }
      .reply-user-info {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        .reply-user-name { font-weight: 500; color: $text-primary; }
        .reply-to-text { color: $text-secondary; }
        .reply-target { color: $primary; }
      }
      .reply-time {
        font-size: 11px;
        color: $text-secondary;
        margin-left: auto;
      }
    }
    .reply-text {
      font-size: 14px;
      line-height: 1.5;
      color: $text-secondary;
      margin: 0 0 0 42px;
    }
  }
  .reply-actions {
    margin-left: 12px;
  }
}

.load-more {
  text-align: center;
  padding: 24px;
  .load-more-btn {
    color: $primary;
    font-size: 14px;
    &:hover {
      text-decoration: underline;
    }
  }
}

/* 右侧回复面板 */
.reply-panel {
  border-radius: 24px;
  border: 1px solid $border-light;
  box-shadow: $shadow-lg;
  background: $bg-card;
  :deep(.el-card__header) {
    padding: 18px 24px;
    border-bottom: 1px solid $border-light;
  }
  :deep(.el-card__body) {
    padding: 24px;
  }
}

.reply-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  .reply-to-user {
    display: flex;
    gap: 14px;
    h4 {
      font-size: 16px;
      font-weight: 600;
      margin: 0 0 4px 0;
      color: $text-primary;
    }
    .original-content {
      font-size: 13px;
      color: $text-secondary;
      margin: 0;
      font-style: italic;
    }
  }
  .close-panel-btn {
    color: $text-secondary;
    &:hover { color: $text-primary; }
  }
}

.reply-input-area {
  margin-bottom: 24px;
  .reply-textarea {
    margin-bottom: 16px;
    :deep(.el-textarea__inner) {
      border-radius: 16px;
      border-color: $border-light;
    }
  }
  .reply-submit-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
}

.existing-replies {
  border-top: 1px solid $border-light;
  padding-top: 20px;
  h4 {
    font-size: 15px;
    font-weight: 500;
    color: $text-primary;
    margin: 0 0 14px 0;
  }
  .replies-scroll {
    max-height: 280px;
    overflow-y: auto;
    padding-right: 6px;
  }
  .existing-reply-item {
    padding: 12px 0;
    border-bottom: 1px solid $border-light;
    &:last-child { border-bottom: none; }
    .reply-user {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;
      .reply-user-name { font-size: 13px; font-weight: 500; }
    }
    .reply-content-preview {
      font-size: 13px;
      color: $text-secondary;
      margin: 0 0 4px 42px;
      line-height: 1.4;
    }
    .reply-time {
      font-size: 11px;
      color: $text-secondary;
      margin-left: 42px;
    }
  }
}

.empty-state, .loading-container {
  padding: 40px 20px;
}

.mt-3 { margin-top: 16px; }

/* 滚动条自定义 */
::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
::-webkit-scrollbar-track {
  background: var(--sp-surface-muted);
  border-radius: 4px;
}
::-webkit-scrollbar-thumb {
  background: var(--sp-border-strong);
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: var(--sp-text-muted);
}

/* 响应式 */
@media (max-width: 1024px) {
  .main-content {
    gap: 20px;
  }
  .right-column {
    width: 340px;
  }
}
@media (max-width: 768px) {
  .forum-home-container {
    padding: 16px;
  }
  .page-header {
    padding: 24px 20px;
    margin-bottom: 20px;
    .page-title { font-size: 24px; }
  }
  .main-content {
    flex-direction: column;
  }
  .right-column {
    width: 100%;
    position: static;
    height: auto;
    margin-top: 20px;
  }
  .comment-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .comment-actions {
    align-self: flex-end;
  }
  .replies-list {
    padding: 0 16px 12px;
  }
  .reply-item .reply-content .reply-text {
    margin-left: 36px;
  }
}
</style>
