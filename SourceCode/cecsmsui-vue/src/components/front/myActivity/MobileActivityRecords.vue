<template>
  <div class="mobile-activity-records" aria-label="活动记录列表">
    <article v-for="(item, index) in items" :key="item.id || index">
      <header><div><small>ACTIVITY</small><h3>{{ item.activityName || '社区活动' }}</h3></div><el-tag effect="plain">{{ item.myState || item.aState || '状态待确认' }}</el-tag></header>
      <dl>
        <div><dt>活动类型</dt><dd>{{ item.type || '未分类' }}</dd></div>
        <div><dt>活动时间</dt><dd>{{ item.activityDate || '待确认' }} {{ item.startTime || '' }}</dd></div>
        <div><dt>活动地点</dt><dd>{{ item.activityAddress || '待确认' }}</dd></div>
        <div><dt>负责人</dt><dd>{{ item.name || '待确认' }} {{ item.telephone || '' }}</dd></div>
        <div><dt>活动积分</dt><dd>{{ item.activityPoint ?? 0 }}</dd></div>
      </dl>
      <footer v-if="cancellable && item.myState === '报名成功' && item.aState !== '已结束'">
        <el-popconfirm title="确定要取消报名吗？" confirm-button-text="确定" cancel-button-text="返回" @confirm="$emit('cancel', item)">
          <template #reference><el-button type="danger" plain>取消报名</el-button></template>
        </el-popconfirm>
      </footer>
    </article>
    <el-empty v-if="!items.length" description="当前分类暂无活动记录" :image-size="72" />
  </div>
</template>

<script setup>
defineProps({ items: { type: Array, default: () => [] }, cancellable: { type: Boolean, default: false } })
defineEmits(['cancel'])
</script>

<style scoped>
.mobile-activity-records { display: none; }
@media (max-width: 760px) {
  .mobile-activity-records { display: grid; gap: 12px; }
  article { padding: 16px; background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
  header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .1em; }
  h3 { margin: 5px 0 0; color: var(--sp-text); font-size: 16px; line-height: 1.35; }
  dl { display: grid; gap: 8px; margin: 15px 0 0; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  dl div { display: grid; grid-template-columns: 70px minmax(0, 1fr); gap: 8px; font-size: 12px; line-height: 1.5; }
  dt { color: var(--sp-text-muted); } dd { min-width: 0; margin: 0; color: var(--sp-text-secondary); overflow-wrap: anywhere; }
  footer { display: flex; justify-content: flex-end; margin-top: 12px; }
  footer :deep(.el-button) { min-height: 40px; }
}
</style>
