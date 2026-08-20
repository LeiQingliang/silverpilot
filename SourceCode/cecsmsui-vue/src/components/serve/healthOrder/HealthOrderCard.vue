<template>
  <el-card class="box-card">
    <template #header>
      <div class="card-header">
        <span>健康服务订单管理</span>
      </div>
    </template>
    <HealthOrderTable />
    <HealthOrderDialog />
  </el-card>
</template>

<script setup>
import HealthOrderTable from './HealthOrderTable.vue'
import HealthOrderDialog from './HealthOrderDialog.vue'
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
</style>
