<template>
  <el-dialog
    width="60%"
    v-model="dialogFormVisible"
    :title="dialogFormTitle"
    :before-close="handleClose"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :destroy-on-close="true"
    :append-to-body="true"
    :modal-append-to-body="true"
    modal-class="no-animate-dialog topmost-dialog"
    center
  >
    <el-form ref="formData" :model="formdata" label-width="100px">
      <!-- 第一行：活动名称 + 活动类别 -->
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="活动名称">
            <el-input v-model="formdata.activityName" autocomplete="off" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="活动类别">
            <el-select v-model="activityTypeId" placeholder="请选择活动类别" @change="handleType" style="width: 100%">
              <el-option v-for="item in typesData" :key="item.id" :label="item.type" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 第二行：活动日期 + 开始时间 + 结束时间 -->
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="活动日期">
            <el-date-picker
              v-model="formdata.activityDate"
              type="date"
              placeholder="选择活动日期"
              :disabledDate="hiredateDisabledDate"
              :shortcuts="hiredateShortcuts"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              teleported
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="开始时间">
            <el-time-picker
              v-model="formdata.startTime"
              placeholder="请选择开始时间"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
              style="width: 100%"
              teleported
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="结束时间">
            <el-time-picker
              v-model="formdata.endTime"
              placeholder="请选择结束时间"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
              style="width: 100%"
              teleported
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 第三行：活动地点 + 负责人 -->
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="活动地点">
            <el-input v-model="formdata.activityAddress" autocomplete="off" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="负责人">
            <el-select v-model="directorId" placeholder="请选择负责人" @change="handleDirector" style="width: 100%">
              <el-option v-for="item in directorsData" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 第四行：人员上限 + 活动积分 -->
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="人员上限">
            <el-input v-model="formdata.limitNum" autocomplete="off" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="活动积分">
            <el-input v-model="formdata.activityPoint" autocomplete="off" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 第五行：活动图片（单独一行，占满宽度） -->
      <el-row>
        <el-col :span="24">
          <el-form-item label="活动图片" label-width="100px">
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
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="canelDialog">取消</el-button>
        <el-button type="primary" @click="toSave" :icon="Promotion">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onUnmounted, nextTick } from 'vue'
import { Promotion, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useActivityStore } from '../../../stores/activity'
import { prepareImageUpload } from '../../../utils/upload.js'

const activityStore = useActivityStore()
const { dialogFormTitle, dialogFormVisible, directorsData, typesData, formdata } = storeToRefs(activityStore)

const activityTypeId = ref(null)
const directorId = ref(null)

// 监听弹窗打开时初始化类型和负责人ID
watch(
  () => dialogFormVisible.value,
  async (visible) => {
    if (visible && activityStore.formdateCopy?.activityType?.id) {
      await nextTick()
      activityTypeId.value = activityStore.formdateCopy.activityType.id
      directorId.value = activityStore.formdateCopy.director?.id || null
    }
  },
  { immediate: true, flush: 'post' }
)

const toSave = () => {
  activityStore.formdata.activityTypeId = activityTypeId.value
  activityStore.formdata.dId = directorId.value
  activityStore.save()
}

const handleType = () => {
  // 类型改变时无需额外操作，store 会自动更新
}

const handleDirector = () => {
  // 负责人改变时无需额外操作
}

const action = '/api/upload/image'
const uploadToken = sessionStorage.getItem('token') || localStorage.getItem('token')
const uploadHeaders = uploadToken ? { Authorization: `Bearer ${uploadToken}` } : {}
const handleImageSuccess = (res) => {
  if (res?.code === 200 && res.result) {
    activityStore.formdata.image = res.result
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

const canelDialog = () => {
  activityStore.dialogFormVisible = false
  activityStore.formdata = {}
  activityStore.formdateCopy.activityType = {}
  activityStore.formdateCopy.director = {}
  activityTypeId.value = null
  directorId.value = null
}

const handleClose = () => {
  canelDialog()
}

// 禁用今天之前的日期
const hiredateDisabledDate = (time) => time.getTime() < new Date().getTime()
// 快捷选项
const hiredateShortcuts = [
  {
    text: '明天',
    value: () => {
      let now = new Date().getTime()
      now += 86400000
      return new Date().setTime(now)
    }
  },
  {
    text: '后天',
    value: () => {
      let now = new Date().getTime()
      now += 86400000 * 2
      return new Date().setTime(now)
    }
  },
  {
    text: '7天后',
    value: () => {
      let now = new Date().getTime()
      now += 86400000 * 7
      return new Date().setTime(now)
    }
  }
]

// 组件卸载时清理状态
onUnmounted(() => {
  if (dialogFormVisible.value) {
    canelDialog()
  }
})
</script>

<style scoped>
.avatar-uploader {
  width: 100%;
  display: block;
}
.avatar {
  width: 100%;
  max-width: 200px;
  border-radius: 6px;
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

/* 确保弹窗在最顶层 */
.topmost-dialog {
  z-index: 3000 !important;
}
.topmost-dialog .el-dialog {
  z-index: 3001 !important;
}
/* 同时提高内部下拉框的层级 */
.el-select__popper,
.el-picker__popper {
  z-index: 3100 !important;
}
</style>
