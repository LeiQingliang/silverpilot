<template>
  <!-- 新增/编辑服务类型 -->
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
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
    <el-form :model="formdata" label-width="27%">
      <el-form-item label="服务类型名称">
        <el-input v-model="formdata.serviceName" style="width: 70%;" />
      </el-form-item>
      <el-form-item
        label="活动图片"
        label-width="27%"
        v-if="dialogTitle == '编辑服务子类别' || dialogTitle == '新增服务子类别'"
      >
        <el-upload
          class="avatar-uploader"
          name="imageFile"
          :action="action"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleImageSuccess"
          :before-upload="beforeAvatarUpload"
        >
          <el-image v-if="formdata.image" :src="formdata.image" class="avatar" />
          <el-icon v-else class="avatar-uploader-icon">
            <Plus />
          </el-icon>
        </el-upload>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formdata.state">
          <el-radio :value="0">禁用</el-radio>
          <el-radio :value="1">启用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="cancelDialog">取消</el-button>
        <el-button type="primary" @click="toSave">提交</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from "pinia";
import { useServicetypeStore } from "../../../stores/servicetype.js";
import { prepareImageUpload } from '../../../utils/upload.js'

const servicetypeStore = useServicetypeStore()
const { formdata, dialogTitle, dialogVisible } = storeToRefs(servicetypeStore)

const action = '/api/upload/image'
const uploadToken = sessionStorage.getItem('token') || localStorage.getItem('token')
const uploadHeaders = uploadToken ? { Authorization: `Bearer ${uploadToken}` } : {}
const handleImageSuccess = (res) => {
  if (res?.code === 200 && res.result) {
    servicetypeStore.formdata.image = res.result
  } else {
    ElMessage.error(res?.msg || '图片上传失败')
  }
}

const beforeAvatarUpload = async (file) => {
  try {
    return await prepareImageUpload(file)
  } catch (error) {
    ElMessage.error(error.message || '图片处理失败')
    return false
  }
}

const cancelDialog = () => {
  servicetypeStore.dialogVisible = false
  servicetypeStore.dialogTitle = ''
  servicetypeStore.formdata = {}
}
const handleClose = () => {
  servicetypeStore.dialogVisible = false
  servicetypeStore.dialogTitle = ''
  servicetypeStore.formdata = {}
}

const toSave = () => {
  servicetypeStore.save()
}
</script>

<style scoped>
.avatar-uploader {
  width: 70%;
  display: block;
}
.avatar {
  width: 100%;
}
.avatar-uploader .el-upload {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}
.avatar-uploader .el-upload:hover {
  border-color: var(--el-color-primary);
}
.el-icon.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 178px;
  height: 178px;
  text-align: center;
}
</style>

<!-- 全局样式：禁用弹窗动画，确保最顶层 -->
<style>
/* 弹窗本身禁用动画 */
.no-animate-dialog .el-dialog {
  animation: none !important;
  transition: none !important;
}
.no-animate-dialog .el-overlay-dialog {
  overflow: auto;
}
/* 所有浮层禁用动画，防止闪烁 */
.el-picker__popper,
.el-date-picker,
.el-time-panel,
.el-select__popper {
  animation: none !important;
  transition: none !important;
}
/* 强制最顶层 */
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
