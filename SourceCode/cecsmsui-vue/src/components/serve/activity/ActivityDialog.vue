<template>
  <el-dialog
    width="60%"
    translate="no"
    v-model="dialogFormVisible"
    :title="dialogFormTitle"
    :show-close="!saving"
    :before-close="handleClose"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :destroy-on-close="true"
    :append-to-body="true"
    :modal-append-to-body="true"
    modal-class="no-animate-dialog topmost-dialog"
    center
  >
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon class="save-error" />
    <el-form ref="formData" :model="formdata" :disabled="saving" label-width="100px">
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
          <el-form-item label="活动日期" @input.capture="captureDateInput">
            <el-date-picker
              v-model="formdata.activityDate"
              type="date"
              placeholder="选择活动日期"
              :disabled-date="hiredateDisabledDate"
              :shortcuts="hiredateShortcuts"
              popper-class="activity-date-popper"
              @clear="clearDateInput"
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
        <el-button @click="canelDialog" :disabled="saving">取消</el-button>
        <el-button type="primary" @click="toSave" :icon="Promotion" :loading="saving">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { Promotion, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useActivityStore } from '../../../stores/activity'
import { prepareImageUpload } from '../../../utils/upload.js'
import { activityDateShortcut, getActivityDateError, isActivityDateDisabled } from '../../../utils/activity-date.js'

const activityStore = useActivityStore()
const { dialogFormTitle, dialogFormVisible, directorsData, typesData, formdata, saving, saveError } = storeToRefs(activityStore)

const activityTypeId = ref(null)
const directorId = ref(null)
const dateInput = ref(null)

const captureDateInput = (event) => {
  dateInput.value = event.target.value
}
const clearDateInput = () => {
  dateInput.value = null
}

// The picker also emits change when it normalizes typed input (e.g. Feb 31).
// Only an explicit calendar/shortcut selection should replace that raw draft.
// Its panel is teleported, so scope these capture listeners to this picker.
const captureDateSelection = (event) => {
  if (!dialogFormVisible.value || !(event.target instanceof Element)) return
  const target = event.target.closest('.activity-date-popper .el-date-table td, .activity-date-popper .el-picker-panel__shortcut')
  if (!target || target.getAttribute('aria-disabled') === 'true' || target.classList.contains('disabled')) return
  if (event.type === 'keydown' && !['Enter', ' ', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) return
  clearDateInput()
}
onMounted(() => {
  document.addEventListener('click', captureDateSelection, true)
  document.addEventListener('keydown', captureDateSelection, true)
})

// 监听弹窗打开时初始化类型和负责人ID
watch(
  () => dialogFormVisible.value,
  (visible) => {
    clearDateInput()
    if (visible) {
      activityTypeId.value = formdata.value.activityTypeId ?? formdata.value.activityType?.id ?? null
      directorId.value = formdata.value.dId ?? formdata.value.director?.id ?? null
    }
  },
  { immediate: true, flush: 'post' }
)

const toSave = async () => {
  if (saving.value) return
  // Let the picker's blur/change handlers finish before taking the draft.
  await nextTick()
  const date = dateInput.value === null ? formdata.value.activityDate : dateInput.value.trim()
  activityStore.saveError = getActivityDateError(date, Number(formdata.value.id) > 0)
  if (activityStore.saveError) {
    if (date) activityStore.saveError += `（输入：${date}）`
    return
  }
  // Invalid/disabled typed input may never emit update:modelValue. Validate
  // the actual typed date so an old model value cannot be silently submitted.
  activityStore.formdata.activityDate = date
  activityStore.formdata.activityTypeId = activityTypeId.value
  activityStore.formdata.dId = directorId.value
  await activityStore.save()
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
  if (saving.value) return
  activityStore.dialogFormVisible = false
  activityStore.resetEditor()
  activityTypeId.value = null
  directorId.value = null
}

const handleClose = () => {
  canelDialog()
}

// Editing also supports correcting historical records. New activities may
// start today; comparing against the current instant wrongly disables today.
const hiredateDisabledDate = (time) => isActivityDateDisabled(time, Number(formdata.value.id) > 0)
// 快捷选项
const hiredateShortcuts = [
  {
    text: '明天',
    value: () => activityDateShortcut(1)
  },
  {
    text: '后天',
    value: () => activityDateShortcut(2)
  },
  {
    text: '7天后',
    value: () => activityDateShortcut(7)
  }
]

// 组件卸载时清理状态
onUnmounted(() => {
  document.removeEventListener('click', captureDateSelection, true)
  document.removeEventListener('keydown', captureDateSelection, true)
  if (dialogFormVisible.value) {
    canelDialog()
  }
})
</script>

<style scoped>
.save-error {
  margin-bottom: 16px;
}
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
