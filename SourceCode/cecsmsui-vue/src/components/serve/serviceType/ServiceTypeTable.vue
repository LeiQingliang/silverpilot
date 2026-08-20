<template>
    <el-table :data="treeData" height="450" class="service-table" row-key="id"
        :table-layout="tableLayout" :row-class-name="tableRowClassName"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }">
        <el-table-column type="expand">
            <template #default="props">
                <el-table :data="props.row.children" class="child-table" row-key="id">
                    <el-table-column prop="serviceName" label="子服务名称" />
                    <el-table-column prop="image" label="图标">
                        <template #default="scope">
                            <el-image v-if="scope.row.image" :src="scope.row.image" class="service-icon"
                                :preview-src-list="[scope.row.image]" />
                        </template>
                    </el-table-column>
                    <el-table-column label="状态">
                        <template #default="scope">
                            <span :class="['status-badge', scope.row.state == '1' ? 'status-enabled' : 'status-disabled']">
                                {{ scope.row.state == '1' ? '已启用' : '已禁用' }}
                            </span>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" fixed="right">
                        <template #default="scope">
                            <el-button size="small" type="primary" @click="handleEditChildren(scope.$index, scope.row)">
                                编辑
                            </el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </template>
        </el-table-column>
        <el-table-column prop="id" label="序号" />
        <el-table-column prop="serviceName" label="服务类别名称" />
        <el-table-column label="状态">
            <template #default="scope">
                <span :class="['status-badge', scope.row.state == '1' ? 'status-enabled' : 'status-disabled']">
                    {{ scope.row.state == '1' ? '已启用' : '已禁用' }}
                </span>
            </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right">
            <template #default="scope">
                <el-button size="small" type="primary" @click="handleEdit(scope.$index, scope.row)">编辑</el-button>
                <el-button size="small" @click="handleAddChildren(scope.$index, scope.row)">新增子类别</el-button>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storeToRefs } from "pinia"
import { useServicetypeStore } from "../../../stores/servicetype.js"

const servicetypeStore = useServicetypeStore()
const { treeData } = storeToRefs(servicetypeStore)
const tableLayout = ref('auto')

const tableRowClassName = ({ rowIndex }) => {
    return rowIndex % 2 === 1 ? 'success-row' : ''
}

onMounted(async () => {
    try {
        await servicetypeStore.selectAllFather()
        await servicetypeStore.fetchChildrenForParents()
    } catch (error) {
        console.error('Failed to load data:', error)
    }
})

const handleEdit = (index, row) => {
    servicetypeStore.pre4EditType(index, row)
}

const handleAddChildren = (index, row) => {
    servicetypeStore.pre4AddChildren(index, row)
}

const handleEditChildren = (index, row) => {
    servicetypeStore.pre4EditChildren(index, row)
}
</script>

<style scoped>
/* ========== 主表格样式 ========== */
.service-table {
    width: 100%;
    margin: 0 auto;
    border-radius: 12px;
    overflow: hidden;
    font-size: 14px;
    --el-table-border-color: #eef2f6;
    --el-table-header-bg-color: #f8fafc;
}

.service-table :deep(.el-table__header th) {
    font-weight: 600;
    color: #1f2f3d;
    background-color: #f8fafc;
    border-bottom: 1px solid #eef2f6;
    padding: 12px 0;
}

.service-table :deep(.el-table__body tr:hover > td) {
    background-color: #f5f9ff !important;
}

.service-table :deep(.success-row) {
    --el-table-tr-bg-color: #f8fafc;
}

/* ========== 展开行区域 ========== */
.service-table :deep(.el-table__expanded-cell) {
    background-color: #fafcff;
    padding: 20px 32px;
}

/* ========== 内嵌子表格 ========== */
.child-table {
    width: 100%;
    margin: 0 auto;
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    border: 1px solid #eef2f6;
}

.child-table :deep(.el-table__header th) {
    background-color: #ffffff;
    font-weight: 500;
    color: #5a6874;
    border-bottom: 1px solid #eef2f6;
}

.child-table :deep(.el-table__body td) {
    background-color: #ffffff;
    border-bottom: 1px solid #f0f2f5;
}

/* ========== 状态徽章 ========== */
.status-badge {
    display: inline-block;
    padding: 2px 12px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.5;
}

.status-enabled {
    background-color: #e8f5e9;
    color: #2e7d32;
}

.status-disabled {
    background-color: #ffebee;
    color: #c62828;
}

/* ========== 图标图片 ========== */
.service-icon {
    width: 100%;
    height: 92px;
    border-radius: 8px;
    object-fit: cover;
}

/* ========== 按钮样式 ========== */
.el-button--small {
    border-radius: 6px;
    padding: 5px 12px;
    font-size: 12px;
    transition: all 0.2s;
}

.el-button--primary {
    background-color: #409eff;
    border-color: #409eff;
}

.el-button--primary:hover {
    background-color: #66b1ff;
    border-color: #66b1ff;
}

.el-button--default {
    background-color: #f5f7fa;
    border-color: #dce3e8;
    color: #5a6874;
}

.el-button--default:hover {
    background-color: #e9ecef;
    border-color: #bdc6cf;
    color: #409eff;
}
</style>