<template>
    <el-card class="activity-type-card">
        <template #header>
            <div class="card-header">
                <span class="card-title">活动类型管理</span>
                <div class="card-actions">
                    <TypeSearch />
                    <el-button type="success" :icon="Plus" @click="btn_add" class="add-btn">新增</el-button>
                </div>
            </div>
        </template>
        <TypeTable />
        <TypeDialog />
    </el-card>
</template>

<script setup>
import TypeSearch from './TypeSearch.vue'
import TypeDialog from './ActivityTypeDialog.vue'
import TypeTable from './TypeTable.vue'
import { Plus } from '@element-plus/icons-vue'
import { useActivitytypeStore } from "../../../stores/activitytype";

const activitytypeStore = useActivitytypeStore()
const btn_add = () => {
    activitytypeStore.preInfo4Add()
}
</script>

<style scoped>
.activity-type-card {
    width: 100%;
    height: 100%;
    border-radius: 16px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    transition: box-shadow 0.3s ease;
    border: none;
    margin-top: 12px;
}

.activity-type-card:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
}

.card-title {
    font-size: 18px;
    font-weight: 600;
    color: #1f2f3d;
    line-height: 1.4;
}

.card-actions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.add-btn {
    border-radius: 8px;
    padding: 8px 16px;
    font-weight: 500;
    transition: all 0.2s ease;
}

.add-btn:hover {
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(103, 194, 58, 0.2);
}

/* 覆盖 Element Plus 卡片默认样式 */
:deep(.el-card__header) {
    padding: 16px 20px;
    border-bottom: 1px solid #eef2f6;
}

:deep(.el-card__body) {
    padding: 20px;
}

/* 可选：如果之前有其他全局样式影响 */
.input-with-select .el-input-group__prepend {
    background-color: var(--el-fill-color-blank);
}
</style>
