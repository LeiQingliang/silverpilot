<template>
    <el-card class="box-card">
        <template #header>
            <div class="card-header">
                <span>服务订单管理</span>
            </div>
        </template>
        <ServiceOrderTable></ServiceOrderTable>
        <ServiceOrderDialog></ServiceOrderDialog>
    </el-card>
</template>

<script setup>
import ServiceOrderTable from './ServiceOrderTable.vue'
import ServiceOrderDialog from './ServiceOrderDialog.vue'
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

.input-with-select .el-input-group__prepend {
    background-color: var(--el-fill-color-blank);
}
</style>
