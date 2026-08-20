<template>
    <!-- 办理服务订单 -->
    <el-dialog v-model="dialogFormVisible" title="服务人员信息" align-center width="30%" :before-close="handleClose">
        <el-form :model="updateFrom" ref="ruleFormRef" label-width="120px">
            <el-form-item label="姓名">
                <el-input v-model="updateFrom.name" style="width: 200px;" />
            </el-form-item>
            <el-form-item label="联系电话">
                <el-input v-model="updateFrom.telephone" style="width: 200px;" />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="cancelDialog">取消</el-button>
                <el-button type="primary" @click="serviceorderStore.updateOrder">
                    提交
                </el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useServiceorderStore } from '../../../stores/serviceorder'
const serviceorderStore = useServiceorderStore()
const { dialogFormVisible, updateFrom } = storeToRefs(serviceorderStore)
const cancelDialog = () => {
    serviceorderStore.dialogFormVisible = false
    serviceorderStore.updateFrom = {}
}
const handleClose = () => {
    cancelDialog()
}
</script>

<style scoped>
.el-button--text {
    margin-right: 15px;
}

.dialog-footer button:first-child {
    margin-right: 10px;
}
</style>
