<template>
    <el-card class="box-card">
        <template #header>
            <div class="card-header">
                <span>服务类型管理</span>
                <div style="display: flex">
                    <el-button style="margin-left:20px" :icon="Plus" @click="btn_add">新增</el-button>
                </div>
            </div>
        </template>
        <ServiceTypeDialog></ServiceTypeDialog>
        <ServiceTypeTable></ServiceTypeTable>
    </el-card>
</template>
<script setup>
import ServiceTypeDialog from './ServiceTypeDialog.vue'
import ServiceTypeTable from './ServiceTypeTable.vue'
import { Plus } from '@element-plus/icons-vue'

import { useServicetypeStore } from "../../../stores/servicetype";
const servicetypeStore = useServicetypeStore()
const btn_add = () => {
    servicetypeStore.pre4Add()
}
</script>

<style scoped>
.box-card {
    width: 100%;
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
