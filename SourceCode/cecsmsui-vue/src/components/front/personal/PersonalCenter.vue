<template>
  <div class="personal-center">
    <el-card class="profile-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="user-info">
            <el-avatar :size="64" :icon="UserFilled" :src="avatarUrl" class="avatar">
              <span v-if="!avatarUrl">{{ userInitial }}</span>
            </el-avatar>
            <div class="user-text">
              <h2>{{ ruleForm.name || ruleForm.username }}</h2>
              <p>ID: {{ ruleForm.id }}</p>
            </div>
          </div>

        </div>
      </template>

      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="rules"
        label-width="100px"
        class="personal-form"
        :size="formSize"
        status-icon
      >
        <el-row :gutter="24">
          <!-- 左列：基础信息（可编辑） -->
          <el-col :xs="24" :sm="24" :md="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="ruleForm.username" readonly class="readonly-input" />
            </el-form-item>
            <el-form-item label="姓名" prop="name">
              <el-input v-model="ruleForm.name" :readonly="readonly" />
            </el-form-item>
            <el-form-item label="性别" prop="sex">
              <el-radio-group v-model="ruleForm.sex" :disabled="readonly">
                <el-radio value="男">男</el-radio>
                <el-radio value="女">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="出生日期" prop="birthday">
              <el-date-picker
                v-model="ruleForm.birthday"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                :disabled="readonly"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="身份证号" prop="idNum">
              <el-input v-model="ruleForm.idNum" :readonly="readonly" />
            </el-form-item>
          </el-col>

          <!-- 右列：积分与联系信息（积分只读，电话地址可编辑） -->
          <el-col :xs="24" :sm="24" :md="12">
            <el-form-item label="我的积分" prop="point">
              <el-input v-model="ruleForm.point" readonly class="readonly-input">
                <template #append>
                  <el-button link @click="goPointsMall">积分商城</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="联系电话" prop="telephone">
              <el-input v-model="ruleForm.telephone" :readonly="readonly" />
            </el-form-item>
            <el-form-item label="居住地址" prop="address">
              <el-input v-model="ruleForm.address" :readonly="readonly" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item class="form-actions">
          <el-button type="primary" :icon="EditPen" @click="enableEditing">编辑</el-button>
          <el-button type="success" :icon="Check" :disabled="readonly" @click="saveProfile">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { ElNotification } from 'element-plus'
import { UserFilled, EditPen, Check } from '@element-plus/icons-vue'
import $axios from '../../../utils/axios'

const formSize = ref('default')
const ruleFormRef = ref()
const ruleForm = ref({})

// 初始化用户数据
const init = async () => {
  const userId = JSON.parse(sessionStorage.getItem('user'))?.id
  if (!userId) return
  const { data: res } = await $axios.get(`/user/selectById/${userId}`)
  if (res.code === 200) {
    ruleForm.value = res.result
    ruleForm.value.sex = res.result.sex?.toString() || ''
  }
}
init()

// 头像占位
const avatarUrl = ref('')
const userInitial = computed(() => {
  const name = ruleForm.value.name || ruleForm.value.username || '用户'
  return name.charAt(0).toUpperCase()
})

const readonly = ref(true)

const enableEditing = () => {
  readonly.value = false
}

const saveProfile = async () => {
  const formEl = ruleFormRef.value
  if (!formEl) return
  try {
    await formEl.validate()
  } catch {
    return
  }

  const payload = {
    id: ruleForm.value.id,
    name: ruleForm.value.name,
    sex: ruleForm.value.sex,
    birthday: ruleForm.value.birthday,
    idNum: ruleForm.value.idNum,
    telephone: ruleForm.value.telephone,
    address: ruleForm.value.address
  }
  const { data: res } = await $axios.post('/user/update', payload)
  if (res.code === 200) {
    const user = JSON.parse(sessionStorage.getItem('user'))
    user.name = payload.name
    sessionStorage.setItem('user', JSON.stringify(user))
    ElNotification({ title: '成功', message: '已保存修改！', type: 'success' })
    readonly.value = true
  } else {
    ElNotification({ title: '失败', message: res.msg || '修改信息失败！', type: 'error' })
  }
}

// 表单校验规则
const rules = reactive({
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  sex: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ],
  birthday: [
    { required: true, message: '请选择出生日期', trigger: 'change' }
  ],
  idNum: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/, message: '身份证号码格式不正确', trigger: 'blur' }
  ],
  telephone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3456789]\d{9}$/, message: '手机号码格式不正确', trigger: 'blur' }
  ],
  address: [
    { required: true, message: '请输入居住地址', trigger: 'blur' }
  ]
})

// 积分商城跳转
const goPointsMall = () => {
  ElNotification({
    title: '提示',
    message: '积分商城开发中',
    type: 'info'
  })
}
</script>

<style scoped>
/* 原有样式保持不变，可沿用之前的风格 */
.personal-center {
  min-height: calc(100vh - var(--sp-header-height));
  background: var(--sp-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.profile-card {
  width: 100%;
  max-width: 1000px;
  border-radius: 24px;
  border: 1px solid var(--sp-border);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: var(--sp-shadow-md);
}

.profile-card:hover {
  border-color: color-mix(in srgb, var(--sp-brand) 28%, var(--sp-border));
  box-shadow: var(--sp-shadow-lg);
}

:deep(.el-card__header) {
  border-bottom: 1px solid var(--sp-border);
  padding: 20px 24px;
  background-color: var(--sp-surface-muted);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  background: linear-gradient(135deg, var(--sp-brand), var(--sp-accent));
  color: var(--sp-on-brand);
  font-weight: 500;
  font-size: 24px;
  box-shadow: var(--sp-shadow-sm);
  transition: transform 0.2s;
}

.avatar:hover {
  transform: scale(1.05);
}

.user-text h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--sp-text);
}

.user-text p {
  margin: 4px 0 0;
  font-size: 0.85rem;
  color: var(--sp-text-muted);
}

.edit-pwd-btn {
  color: var(--sp-brand);
  font-weight: 500;
  transition: all 0.2s;
}

.edit-pwd-btn:hover {
  color: var(--sp-brand-strong);
  background-color: var(--sp-brand-soft);
}

.personal-form {
  padding: 24px 28px 32px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--sp-text-secondary);
}

:deep(.el-input__inner) {
  border-radius: 12px;
  transition: all 0.2s;
}

.readonly-input :deep(.el-input__inner) {
  background-color: var(--sp-surface-muted);
  border-color: var(--sp-border);
  color: var(--sp-text-secondary);
  cursor: default;
}

.readonly-input :deep(.el-input__inner):focus {
  border-color: var(--sp-border);
}

:deep(.el-radio-group .el-radio) {
  margin-right: 16px;
}

:deep(.el-radio__label) {
  color: var(--sp-text-secondary);
}

.form-actions {
  margin-top: 32px;
  margin-bottom: 0;
  text-align: right;
}

.form-actions .el-button {
  padding: 10px 24px;
  border-radius: 40px;
  font-weight: 500;
  transition: all 0.2s;
}

.form-actions .el-button--primary {
  background: linear-gradient(135deg, var(--sp-brand), var(--sp-brand-strong));
  border: none;
  box-shadow: 0 7px 18px color-mix(in srgb, var(--sp-brand) 22%, transparent);
}

.form-actions .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 22px color-mix(in srgb, var(--sp-brand) 30%, transparent);
}

.form-actions .el-button--success {
  background: linear-gradient(135deg, var(--sp-brand), var(--sp-brand-strong));
  border: none;
  box-shadow: 0 7px 18px color-mix(in srgb, var(--sp-brand) 22%, transparent);
}

.form-actions .el-button--success:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 22px color-mix(in srgb, var(--sp-brand) 30%, transparent);
}

.form-actions .el-button--success:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .personal-form {
    padding: 20px;
  }
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .user-info {
    width: 100%;
  }
  .edit-pwd-btn {
    align-self: flex-end;
  }
  :deep(.el-form-item__label) {
    width: 80px !important;
  }
}
</style>
