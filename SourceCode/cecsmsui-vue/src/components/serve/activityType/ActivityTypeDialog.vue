<template>
    <!-- 新增活动类型 -->
    <el-dialog v-model="addDialogVisible" title="新增活动类型" align-center width="30%" :before-close="handleClose">
        <el-form :model="form" ref="ruleFormRef" label-width="120px">
            <el-form-item label="活动类型名称">
                <el-input v-model="form.type" style="width: 200px;" />
            </el-form-item>
            <el-form-item label="活动类型描述">
                <el-input v-model="form.detail" style="width: 200px;" />
            </el-form-item>
            <el-form-item label="活动目的">
                <el-input v-model="form.purpose" style="width: 200px;" />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="cancelDialog()">取消</el-button>
                <el-button type="primary" @click="activitytypeStore.addType">
                    提交
                </el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import { storeToRefs } from "pinia";
import { useActivitytypeStore } from "../../../stores/activitytype";
const activitytypeStore = useActivitytypeStore()
const { addDialogVisible, form } = storeToRefs(activitytypeStore)
const cancelDialog = () => {
    activitytypeStore.addDialogVisible = false
    activitytypeStore.form = {}
}
const handleClose = () => {
    cancelDialog()
}
</script>