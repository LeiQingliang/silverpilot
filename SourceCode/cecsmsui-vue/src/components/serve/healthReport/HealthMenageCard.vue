<template>
    <el-card class="box-card">
        <template #header>
            <div class="card-header">
                <span>健康报告管理</span>
                <el-button type="primary" :icon="Plus" @click="btn_add">新增</el-button>
            </div>
        </template>
        <div class="card-main">
            <UserCard class="health-panel" />
            <ReportCard class="health-panel" />
        </div>
        <AddReportDialog />
        <DetailDialog />
    </el-card>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import UserCard from './UserCard.vue';
import ReportCard from './ReportCard.vue'
import AddReportDialog from './AddReportDialog.vue'
import DetailDialog from './DetailDialog.vue'

import { useReportStore } from "../../../stores/report.js";
const reportStore = useReportStore()
const btn_add = () => {
    reportStore.pre4addReport()
}
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

.card-main {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 14px;
}

.health-panel { min-width: 0; }

@media (max-width: 980px) {
    .card-main { grid-template-columns: 1fr; }
}
</style>
