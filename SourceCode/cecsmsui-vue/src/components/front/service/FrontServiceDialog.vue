<template>
  <el-dialog
    v-model="dialogVisible"
    title="预约服务"
    :before-close="handleClose"
    width="500px"
    class="modern-dialog"
    center
    draggable
  >
    <el-form
      ref="formRef"
      :model="formdata"
      :rules="rules"
      label-width="100px"
      class="modern-form"
    >
      <el-form-item label="预约人">
        <el-input v-model="name" disabled />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="telephone" disabled />
      </el-form-item>
      <el-form-item label="服务类别">
        <el-cascader
          v-model="selectedOptions"
          :options="cascaderOptions"
          :props="cascaderProps"
          :load="loadChildren"
          @change="handleChange"
          clearable
          lazy
          placeholder="请选择服务类别"
        />
      </el-form-item>
      <el-form-item label="预约服务日期" prop="reserveDate">
        <el-date-picker
          v-model="formdata.reserveDate"
          type="date"
          placeholder="请选择预约日期"
          :disabledDate="hiredateDisabledDate"
          :shortcuts="hiredateShortcuts"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="服务地点" prop="serviceAddress">
        <el-input v-model="formdata.serviceAddress" placeholder="请输入服务地点" />
      </el-form-item>
      <el-form-item label="预约详情" prop="orderDetail">
        <el-input
          v-model="formdata.orderDetail"
          type="textarea"
          :rows="3"
          placeholder="简要描述预约详情"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="canelDialog" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="save" :icon="Promotion" class="submit-btn">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted, watchEffect } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from "pinia"
import { useServicetypeStore } from "../../../stores/servicetype.js"
import $axios from '../../../utils/axios.js'

const servicetypeStore = useServicetypeStore()
const { dialogVisible, formdata, cascaderOptions } = storeToRefs(servicetypeStore)
const sessionUser = JSON.parse(sessionStorage.getItem('user') || '{}')
const uId = sessionUser.id
const name = ref(sessionUser.name || sessionUser.username || '')
const telephone = ref('')
const formRef = ref()
const rules = {
  reserveDate: [{ required: true, message: '请选择预约日期', trigger: 'change' }],
  serviceAddress: [
    { required: true, message: '请输入服务地点', trigger: 'blur' },
    { min: 2, max: 255, message: '服务地点需2-255字', trigger: 'blur' }
  ],
  orderDetail: [{ max: 255, message: '预约详情不能超过255字', trigger: 'blur' }]
}

// 级联选择器的 props
const cascaderProps = {
  value: 'id',
  label: 'serviceName',
  children: 'children',
  leaf: 'leaderId',
  lazy: true,
  lazyLoad(node, resolve) {
    servicetypeStore.loadChildrenCategories(node.value).then(children => {
      resolve(children)
    })
  },
}

const selectedOptions = ref([])
let isDefault = true

// 监听默认值
watchEffect(() => {
  if (isDefault) {
    if (servicetypeStore.typeB && servicetypeStore.typeS) {
      selectedOptions.value = [servicetypeStore.typeB.id, servicetypeStore.typeS.id]
    }
  }
})

onMounted(async () => {
  servicetypeStore.loadFatherCategories()
  if (!uId) return
  const { data: res } = await $axios.get(`/user/selectById/${uId}`)
  if (res.code === 200) {
    name.value = res.result.name || name.value
    telephone.value = res.result.telephone || ''
  }
})

function dateFormat() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hour = String(now.getHours()).padStart(2, '0')
  const minute = String(now.getMinutes()).padStart(2, '0')
  const second = String(now.getSeconds()).padStart(2, '0')
  servicetypeStore.formdata.orderDate = `${year}-${month}-${day}`
  servicetypeStore.formdata.orderTime = `${hour}:${minute}:${second}`
}

const save = async () => {
  if (selectedOptions.value.length !== 2) {
    ElMessage.warning('请选择完整的服务类别')
    return
  }
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  dateFormat()
  servicetypeStore.formdata.uId = uId
  servicetypeStore.formdata.typeBId = selectedOptions.value[0]
  servicetypeStore.formdata.typeSId = selectedOptions.value[1]
  servicetypeStore.toOrder()
}

const hiredateDisabledDate = (time) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return time.getTime() < today.getTime()
}
const hiredateShortcuts = [
  { text: '明天', value: () => new Date().setTime(new Date().getTime() + 86400000) },
  { text: '后天', value: () => new Date().setTime(new Date().getTime() + 86400000 * 2) },
  { text: '7天后', value: () => new Date().setTime(new Date().getTime() + 86400000 * 7) }
]

const handleChange = () => {
  isDefault = false
}

const canelDialog = () => {
  servicetypeStore.dialogVisible = false
  servicetypeStore.formdata = {}
}
const handleClose = () => {
  canelDialog()
  isDefault = true
}
</script>

<style scoped>
/* 弹窗整体样式 */
.modern-dialog :deep(.el-dialog) {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  transition: transform 0.2s;
}

.modern-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 8px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.modern-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: #2c3e50;
}

.modern-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.modern-dialog :deep(.el-dialog__footer) {
  padding: 8px 24px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

/* 表单样式 */
.modern-form {
  margin: 0;
}

.modern-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.modern-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #2c3e50;
  font-size: 14px;
}

/* 输入框、级联选择器、日期选择器统一风格 */
.modern-form :deep(.el-input__wrapper),
.modern-form :deep(.el-cascader__wrapper),
.modern-form :deep(.el-date-editor .el-input__wrapper) {
  border-radius: 48px;
  background-color: #f5f7fa;
  box-shadow: none;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.modern-form :deep(.el-input__wrapper:hover),
.modern-form :deep(.el-cascader__wrapper:hover),
.modern-form :deep(.el-date-editor .el-input__wrapper:hover) {
  border-color: #c0c4cc;
}

.modern-form :deep(.el-input__wrapper.is-focus),
.modern-form :deep(.el-cascader__wrapper.is-focus),
.modern-form :deep(.el-date-editor .el-input__wrapper.is-focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.modern-form :deep(.el-textarea__inner) {
  border-radius: 16px;
  background-color: #f5f7fa;
  border: 1px solid transparent;
  transition: all 0.2s;
}

.modern-form :deep(.el-textarea__inner:focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  background-color: #fff;
}

.modern-form :deep(.el-input.is-disabled .el-input__wrapper) {
  background-color: #f5f7fa;
  border-color: transparent;
  opacity: 0.8;
}

/* 级联下拉面板样式优化 */
.modern-form :deep(.el-cascader__dropdown) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
}

/* 日期快捷选项样式 */
.modern-form :deep(.el-picker-panel__shortcut) {
  border-radius: 20px;
  transition: all 0.2s;
}
.modern-form :deep(.el-picker-panel__shortcut:hover) {
  background-color: #ecf5ff;
  color: #409eff;
}

/* 按钮样式 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
}

.cancel-btn,
.submit-btn {
  border-radius: 48px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.cancel-btn {
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #606266;
}
.cancel-btn:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
  color: #409eff;
}

.submit-btn {
  background: linear-gradient(90deg, #409eff, #66b1ff);
  border: none;
  color: #fff;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.3);
}
.submit-btn:hover {
  background: linear-gradient(90deg, #66b1ff, #409eff);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}
</style>
