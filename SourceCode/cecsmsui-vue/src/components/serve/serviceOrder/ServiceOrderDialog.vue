<template>
  <!-- 办理服务订单（受理弹窗） -->
  <el-dialog
    v-model="dialogFormVisible"
    title="服务人员信息"
    align-center
    width="30%"
    :before-close="handleClose"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :destroy-on-close="true"
    :append-to-body="true"
    :modal-append-to-body="true"
    modal-class="topmost-dialog no-animate-dialog"
  >
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

<!-- 全局样式：禁用弹窗动画，确保最顶层 -->
<style>
.no-animate-dialog .el-dialog {
  animation: none !important;
  transition: none !important;
}
.no-animate-dialog .el-overlay-dialog {
  overflow: auto;
}
.el-picker__popper,
.el-date-picker,
.el-time-panel,
.el-select__popper {
  animation: none !important;
  transition: none !important;
}
.topmost-dialog {
  z-index: 3000 !important;
}
.topmost-dialog .el-dialog {
  z-index: 3001 !important;
}
.el-select__popper,
.el-picker__popper {
  z-index: 3100 !important;
}
</style>