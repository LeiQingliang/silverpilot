<template>
  <el-tabs type="border-card" class="demo-tabs">
    <el-tab-pane label="体检报告">
      <el-card class="box-card">
        <el-table class="desktop-report-table" :data="reportData" style="width: 100%" max-height="485">
          <el-table-column label="序号" prop="id" width="64" />
          <el-table-column label="体检日期" prop="time" width="160" />
          <el-table-column label="体检医生" prop="dId" width="120">
            <template #default="scope">
              {{ scope.row.doctor?.name || '待记录' }}
            </template>
          </el-table-column>
          <el-table-column label="姓名" prop="uId">
            <template #default="scope">
              {{ scope.row.user?.name || '未知居民' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="220">
            <template #default="scope">
              <el-button size="small" link type="primary" @click="toDetail(scope.row)">查看</el-button>
              <el-button size="small" link type="success" @click="aiAnalysis(scope.row)" :loading="aiLoading === scope.row.id">健康解读</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mobile-report-list" aria-label="体检报告列表">
          <article v-for="report in reportData" :key="report.id">
            <header><div><small>健康报告</small><h3>{{ report.time || '体检日期待确认' }}</h3></div><span>#{{ report.id }}</span></header>
            <dl>
              <div><dt>体检人</dt><dd>{{ report.user?.name || '当前用户' }}</dd></div>
              <div><dt>体检医生</dt><dd>{{ report.doctor?.name || '待确认' }}</dd></div>
            </dl>
            <footer>
              <el-button plain type="primary" @click="toDetail(report)">查看详情</el-button>
              <el-button type="success" @click="aiAnalysis(report)" :loading="aiLoading === report.id">智能健康解读</el-button>
            </footer>
          </article>
          <el-empty v-if="!reportData.length" description="暂无体检报告" :image-size="72" />
        </div>
      </el-card>
    </el-tab-pane>
  </el-tabs>

  <!-- AI 健康解读结果弹窗 -->
  <el-dialog title="智能健康解读（非医疗诊断）" v-model="aiDialogVisible" width="min(760px, calc(100% - 24px))" :before-close="closeAiDialog">
    <div class="ai-content">{{ plainText }}</div>
    <template #footer>
      <span class="dialog-footer">
        <el-button type="primary" @click="playAudio" :disabled="!aiResult || isPlaying">朗读</el-button>
        <el-button @click="stopAudio" :disabled="!isPlaying">停止</el-button>
        <el-button type="primary" @click="downloadTxt">下载文本</el-button>
        <el-button @click="closeAiDialog">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { storeToRefs } from "pinia";
import { useReportStore } from "../../../stores/report.js";
import axios from '../../../utils/axios'
import { ElMessage } from 'element-plus'
import { saveBlobResponse } from '../../../utils/download.js'

const reportStore = useReportStore()
const { reportData } = storeToRefs(reportStore)
const id = JSON.parse(sessionStorage.getItem('user')).id
reportStore.selectReport(id)

const toDetail = (row) => reportStore.pre4Detail(row)

// AI 诊断相关
const aiLoading = ref(null)
const aiDialogVisible = ref(false)
const aiResult = ref('')   // 原始 AI 返回文本（含 Markdown）
const isPlaying = ref(false)

// 清理 Markdown 标记，转换为纯文本（保留换行）
const cleanMarkdown = (text) => {
  if (!text) return ''
  let cleaned = text
    // 去除粗体 **text** 或 __text__
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    // 去除斜体 *text* 或 _text_
    .replace(/\*(.+?)\*/g, '$1')
    .replace(/_(.+?)_/g, '$1')
    // 去除标题标记 # ## 等
    .replace(/^#+\s+/gm, '')
    // 将无序列表项 * item 或 - item 转换为 • item
    .replace(/^[*-]\s+/gm, '• ')
    // 将数字列表 1. item 保留数字点
    .replace(/^\d+\.\s+/gm, (match) => match)
    // 去除链接 [text](url) 只保留 text
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    // 去除代码块标记
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]+)`/g, '$1')
    // 将多个连续换行压缩为两个换行
    .replace(/\n{3,}/g, '\n\n')
  return cleaned
}

// 获取纯文本（用于语音朗读）
const plainText = computed(() => {
  return cleanMarkdown(aiResult.value)
})

// 文字转语音
let speechUtterance = null
const playAudio = () => {
  if (!plainText.value) {
    ElMessage.warning('没有可朗读的内容')
    return
  }
  // 如果已经在播放，先停止再重新播放（避免重叠）
  if (isPlaying.value) {
    window.speechSynthesis.cancel()
  }
  speechUtterance = new SpeechSynthesisUtterance(plainText.value)
  speechUtterance.lang = 'zh-CN'  // 中文
  speechUtterance.rate = 0.9      // 语速稍慢，适合老年人
  speechUtterance.pitch = 1.0      // 音调正常
  speechUtterance.onstart = () => {
    isPlaying.value = true
  }
  speechUtterance.onend = () => {
    isPlaying.value = false
  }
  speechUtterance.onerror = (event) => {
    console.error('语音合成错误', event)
    ElMessage.error('语音暂停')
    isPlaying.value = false
  }
  window.speechSynthesis.speak(speechUtterance)
}

// 停止播放
const stopAudio = () => {
  if (window.speechSynthesis) {
    window.speechSynthesis.cancel()
    isPlaying.value = false
  }
}

// 下载 TXT 文件
const downloadTxt = async () => {
  if (!aiResult.value) {
    ElMessage.warning('无内容可下载')
    return
  }
  const cleaned = cleanMarkdown(aiResult.value)
  const blob = new Blob([cleaned], { type: 'text/plain;charset=utf-8' })
  try {
    await saveBlobResponse(
      { status: 200, data: blob, headers: { 'content-type': blob.type } },
      `AI健康报告_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.txt`
    )
    ElMessage.success('下载成功')
  } catch (error) {
    ElMessage.error(error.message || '下载失败')
  }
}

const aiAnalysis = async (row) => {
  const reportId = row.id
  aiLoading.value = reportId
  try {
    const response = await axios.get(`/report/analyze/${reportId}`)
    if (response.data.code === 200) {
      aiResult.value = response.data.result   // 原始 Markdown 文本
      aiDialogVisible.value = true
    } else {
      ElMessage.error(response.data.msg || 'AI健康解读失败')
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    aiLoading.value = null
  }
}

const closeAiDialog = () => {
  // 关闭弹窗时停止播放
  if (isPlaying.value) {
    stopAudio()
  }
  aiDialogVisible.value = false
  aiResult.value = ''
}
</script>

<style scoped>
.demo-tabs {
  height: 85vh;
}
.box-card {
  height: 100%;
}
.ai-content {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
  max-height: 500px;
  overflow-y: auto;
  padding: 10px;
}
.mobile-report-list { display: none; }

@media (max-width: 760px) {
  .demo-tabs { height: auto; min-height: 480px; }
  .box-card { height: auto; }
  .desktop-report-table { display: none; }
  .box-card :deep(.el-card__body) { padding: 12px; }
  .mobile-report-list { display: grid; gap: 12px; }
  .mobile-report-list article { padding: 16px; background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  .mobile-report-list header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
  .mobile-report-list header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .1em; }
  .mobile-report-list h3 { margin: 6px 0 0; color: var(--sp-text); font-size: 15px; }
  .mobile-report-list header > span { color: var(--sp-text-muted); font: 700 11px/1 var(--sp-font-mono); }
  .mobile-report-list dl { display: grid; gap: 8px; margin: 14px 0; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  .mobile-report-list dl div { display: grid; grid-template-columns: 70px minmax(0, 1fr); gap: 8px; font-size: 12px; }
  .mobile-report-list dt { color: var(--sp-text-muted); }.mobile-report-list dd { margin: 0; color: var(--sp-text-secondary); }
  .mobile-report-list footer { display: grid; grid-template-columns: 1fr 1.35fr; gap: 8px; }
  .mobile-report-list footer :deep(.el-button) { min-height: 42px; margin: 0; }
  .dialog-footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
  .dialog-footer :deep(.el-button) { margin: 0; }
}
</style>
