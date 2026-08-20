<template>
  <div class="mobile-service-orders" aria-label="服务订单列表">
    <article v-for="(item, index) in items" :key="item.id || index" class="mobile-order-card">
      <header>
        <div>
          <small>ORDER {{ item.id || index + 1 }}</small>
          <h3>{{ serviceName(item) }}</h3>
        </div>
        <el-tag :type="stateMeta(item.orderState).type" effect="plain">{{ stateMeta(item.orderState).label }}</el-tag>
      </header>
      <p class="order-summary">{{ item.orderDetail || '暂无订单说明' }}</p>
      <dl>
        <div><dt>预约时间</dt><dd>{{ item.reserveDate || item.orderDate || '待确认' }} {{ item.reserveTime || item.orderTime || '' }}</dd></div>
        <div><dt>服务地点</dt><dd>{{ item.serviceAddress || '待确认' }}</dd></div>
        <div v-if="item.manager"><dt>受理人员</dt><dd>{{ item.manager.name || '已受理' }} {{ item.manager.telephone || '' }}</dd></div>
        <div v-if="item.name"><dt>服务人员</dt><dd>{{ item.name }} {{ item.telephone || '' }}</dd></div>
      </dl>
      <footer>
        <el-popconfirm v-if="Number(item.orderState) === 0" title="确定要取消预约吗？" confirm-button-text="确定" cancel-button-text="返回" @confirm="$emit('cancel', item)">
          <template #reference><el-button type="danger" plain>取消预约</el-button></template>
        </el-popconfirm>
        <el-popconfirm v-else-if="Number(item.orderState) === 2" title="确认服务已经完成？" confirm-button-text="确定" cancel-button-text="返回" @confirm="$emit('confirm', item)">
          <template #reference><el-button type="primary">确认完成</el-button></template>
        </el-popconfirm>
        <div v-else-if="Number(item.orderState) === 3" class="rating-control">
          <span>服务评分</span>
          <el-rate v-model="item.rate" allow-half clearable aria-label="服务评分" @change="$emit('rate', item)" />
        </div>
        <span v-else class="terminal-state">{{ Number(item.orderState) === 1 ? '订单已取消' : '服务已完成' }}</span>
      </footer>
    </article>
    <el-empty v-if="!items.length" description="当前分类暂无服务订单" :image-size="72" />
  </div>
</template>

<script setup>
defineProps({ items: { type: Array, default: () => [] } })
defineEmits(['cancel', 'confirm', 'rate'])

const serviceName = (item) => {
  const parent = item.typeB?.serviceName
  const child = item.typeS?.serviceName
  return [parent, child].filter(Boolean).join(' / ') || '养老服务订单'
}

const stateMeta = (state) => ({
  0: { label: '待受理', type: 'warning' },
  1: { label: '已取消', type: 'info' },
  2: { label: '待完成', type: 'primary' },
  3: { label: '待评价', type: 'warning' },
  4: { label: '已完成', type: 'success' }
}[Number(state)] || { label: '状态待确认', type: 'info' })
</script>

<style scoped>
.mobile-service-orders { display: none; }
@media (max-width: 760px) {
  .mobile-service-orders { display: grid; gap: 12px; }
  .mobile-order-card { padding: 16px; color: var(--sp-text); background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  .mobile-order-card header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .mobile-order-card header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .1em; }
  .mobile-order-card h3 { margin: 5px 0 0; font-size: 16px; line-height: 1.35; }
  .order-summary { margin: 14px 0; padding: 11px 12px; color: var(--sp-text-secondary); font-size: 13px; line-height: 1.55; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  dl { display: grid; gap: 8px; margin: 0; }
  dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; font-size: 12px; line-height: 1.5; }
  dt { color: var(--sp-text-muted); } dd { min-width: 0; margin: 0; overflow-wrap: anywhere; color: var(--sp-text-secondary); }
  footer { display: flex; align-items: center; justify-content: flex-end; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--sp-border); }
  footer :deep(.el-button) { min-height: 40px; }
  .rating-control { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--sp-text-secondary); font-size: 12px; }
  .terminal-state { color: var(--sp-text-muted); font-size: 12px; }
}
</style>
