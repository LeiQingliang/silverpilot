<template>
  <el-dialog
    v-model="dialogVisible"
    title="留言详情"
    width="600px"
    :before-close="handleClose"
  >
    <div v-if="comment" class="comment-detail">
      <!-- 留言基本信息 -->
      <div class="section">
        <h3>基本信息</h3>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">留言ID：</span>
            <span class="value">{{ comment.id }}</span>
          </div>
          <div class="info-item">
            <span class="label">状态：</span>
            <el-tag
              :type="comment.isDeleted === 1 ? 'danger' : 'success'"
              size="small"
            >
              {{ comment.isDeleted === 1 ? '已删除' : '正常' }}
            </el-tag>
          </div>
          <div class="info-item">
            <span class="label">类型：</span>
            <el-tag
              :type="comment.parentId ? 'warning' : 'primary'"
              size="small"
            >
              {{ comment.parentId ? '回复' : '主留言' }}
            </el-tag>
          </div>
          <div class="info-item">
            <span class="label">发布时间：</span>
            <span class="value">{{ formatTime(comment.createTime) }}</span>
          </div>
          <div v-if="comment.updateTime" class="info-item">
            <span class="label">更新时间：</span>
            <span class="value">{{ formatTime(comment.updateTime) }}</span>
          </div>
        </div>
      </div>

      <!-- 留言内容 -->
      <div class="section">
        <h3>留言内容</h3>
        <div class="content-box">
          {{ comment.content }}
        </div>
      </div>

      <!-- 用户信息 -->
      <div class="section">
        <h3>用户信息</h3>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">用户ID：</span>
            <span class="value">{{ comment.userId }}</span>
          </div>
          <div class="info-item">
            <span class="label">姓名：</span>
            <span class="value">{{ comment.userName || '匿名用户' }}</span>
          </div>
          <div class="info-item">
            <span class="label">电话：</span>
            <span class="value">{{ comment.userTelephone || '-' }}</span>
          </div>
        </div>
      </div>

      <!-- 回复信息 -->
      <div v-if="comment.parentId" class="section">
        <h3>回复信息</h3>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">父留言ID：</span>
            <span class="value">{{ comment.parentId }}</span>
          </div>
          <div class="info-item">
            <span class="label">回复给：</span>
            <span class="value">{{ comment.replyToUserName || '未知用户' }}</span>
          </div>
        </div>
      </div>

      <!-- 父留言内容（如果是回复） -->
      <div v-if="comment.parentId && parentComment" class="section">
        <h3>被回复的留言</h3>
        <div class="parent-comment">
          <div class="parent-user">
            <strong>{{ parentComment.userName || '匿名用户' }}</strong>
            <span class="parent-time">{{ formatTime(parentComment.createTime) }}</span>
          </div>
          <div class="parent-content">
            {{ parentComment.content }}
          </div>
        </div>
      </div>

      <!-- 回复列表（如果是主留言） -->
      <div v-if="!comment.parentId && replies.length > 0" class="section">
        <h3>回复列表（{{ replies.length }}条）</h3>
        <div class="replies-list">
          <div
            v-for="reply in replies"
            :key="reply.id"
            class="reply-item"
          >
            <div class="reply-user">
              <strong>{{ reply.userName || '匿名用户' }}</strong>
              <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
            </div>
            <div class="reply-content">
              回复 {{ reply.replyToUserName || '未知用户' }}: {{ reply.content }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import axios from '@/utils/axios'
import { formatCommentDateTime as formatTime } from '@/utils/comment-time'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  comment: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const parentComment = ref(null)
const replies = ref([])

// 获取父留言或回复列表
const fetchRelatedComments = async () => {
  if (!props.comment) return

  try {
    if (props.comment.parentId) {
      // 获取父留言
      const response = await axios.get(`/comment/detail/${props.comment.parentId}`)
      if (response.data && response.data.code === 200) {
        parentComment.value = response.data.result || response.data.data
      }
    } else {
      // 获取回复列表
      const response = await axios.get(`/comment/replies/${props.comment.id}`)
      if (response.data && response.data.code === 200) {
        replies.value = response.data.result || response.data.data || []
      }
    }
  } catch (err) {
    console.error('获取相关留言失败:', err)
  }
}

// 监听comment变化
watch(
  () => props.comment,
  (newComment) => {
    if (newComment) {
      parentComment.value = null
      replies.value = []
      fetchRelatedComments()
    }
  },
  { immediate: true }
)

const handleClose = () => {
  dialogVisible.value = false
}
</script>

<style scoped>
.comment-detail {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 10px;
}

.section {
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 15px;
}

.section:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.section h3 {
  margin: 0 0 10px 0;
  font-size: 16px;
  color: #303133;
  font-weight: 600;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-item .label {
  color: #606266;
  min-width: 80px;
}

.info-item .value {
  color: #303133;
  font-weight: 500;
}

.content-box {
  background-color: #f8f9fa;
  border-radius: 6px;
  padding: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.parent-comment,
.reply-item {
  background-color: #f8f9fa;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e8e8e8;
}

.parent-user,
.reply-user {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
}

.parent-time,
.reply-time {
  font-size: 12px;
  color: #909399;
}

.parent-content,
.reply-content {
  line-height: 1.5;
  color: #606266;
  font-size: 14px;
}

.replies-list {
  max-height: 200px;
  overflow-y: auto;
}
</style>
