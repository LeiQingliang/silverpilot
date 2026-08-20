<template>
    <el-table :data="tableData" style="width: 100%;margin-left: auto;margin-right: auto;" :table-layout="tableLayout"
        :row-class-name="tableRowClassName">
        <el-table-column prop="id" label="序号" />
        <el-table-column prop="type" label="活动分类" />
        <el-table-column prop="detail" label="活动类型描述" />
        <el-table-column prop="purpose" label="活动目的" />
        <el-table-column label="状态">
            <template #default="{ row }">
                <el-radio-group v-model="row.state" @change="stateChange(row)">
                    <el-radio :value="0">禁用</el-radio>
                    <el-radio :value="1">启用</el-radio>
                </el-radio-group>
            </template>
        </el-table-column>

    </el-table>
</template>
<script setup>
import { ref } from 'vue'
//斑马条
const tableRowClassName = ({ rowIndex }) => {
    return rowIndex % 2 === 1 ? 'success-row' : ''
}
const tableLayout = ref('auto')

import { storeToRefs } from "pinia";
import { useActivitytypeStore } from "../../../stores/activitytype";
const activitytypeStore = useActivitytypeStore()
const { tableData } = storeToRefs(activitytypeStore)
activitytypeStore.init()
const stateChange = (row) => {
    activitytypeStore.formData = row
    activitytypeStore.updateState()
}
</script>
<style>
.el-table .success-row {
    --el-table-tr-bg-color: #c6e2ff
}
</style>
