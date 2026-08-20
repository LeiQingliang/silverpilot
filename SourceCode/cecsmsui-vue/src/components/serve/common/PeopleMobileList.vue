<template>
  <div class="people-mobile-list" :aria-label="`${entityLabel}列表`">
    <article v-for="person in items" :key="person.id" class="person-card">
      <header>
        <div>
          <small>{{ entityLabel.toUpperCase() }} {{ person.id }}</small>
          <h3>{{ person.name || '未命名人员' }}</h3>
        </div>
        <el-tag effect="plain">{{ person.sex || '未知' }}</el-tag>
      </header>
      <dl>
        <div><dt>用户名</dt><dd>{{ person.username || '--' }}</dd></div>
        <div><dt>联系电话</dt><dd>{{ person.telephone || '--' }}</dd></div>
        <div v-if="person.department"><dt>科室/部门</dt><dd>{{ person.department }}</dd></div>
        <div v-if="person.address"><dt>现住址</dt><dd>{{ person.address }}</dd></div>
      </dl>
      <footer>
        <el-button type="primary" plain @click="$emit('edit', person)">修改信息</el-button>
        <el-popconfirm
          width="280"
          confirm-button-text="确定"
          cancel-button-text="取消"
          :icon="InfoFilled"
          icon-color="#b4233b"
          :title="`确定要注销${person.name || '该人员'}吗？`"
          @confirm="$emit('deactivate', person)"
        >
          <template #reference><el-button type="danger" plain>注销账号</el-button></template>
        </el-popconfirm>
      </footer>
    </article>
    <el-empty v-if="!items.length" :description="`暂无${entityLabel}数据`" :image-size="64" />
  </div>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'

defineProps({
  items: { type: Array, default: () => [] },
  entityLabel: { type: String, default: '人员' }
})
defineEmits(['edit', 'deactivate'])
</script>

<style scoped>
.people-mobile-list { display: none; }
@media (max-width: 760px) {
  .people-mobile-list { display: grid; gap: 12px; }
  .person-card { padding: 16px; color: var(--sp-text); background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
  header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
  h3 { margin: 6px 0 0; font-size: 18px; }
  dl { display: grid; gap: 7px; margin: 14px 0; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
  dl div { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: 8px; font-size: 12px; line-height: 1.5; }
  dt { color: var(--sp-text-muted); } dd { min-width: 0; margin: 0; color: var(--sp-text-secondary); overflow-wrap: anywhere; }
  footer { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; padding-top: 12px; border-top: 1px solid var(--sp-border); }
  footer :deep(.el-button) { width: 100%; margin: 0; }
}
</style>
