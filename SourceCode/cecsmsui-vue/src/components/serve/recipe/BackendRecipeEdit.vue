<template>
  <div class="recipe-edit-container">
    <div class="page-header">
      <h1>{{ isEdit ? '编辑菜谱' : '新增菜谱' }}</h1>
      <el-button
        type="info"
        :icon="ArrowLeft"
        @click="goBack"
        class="back-btn"
      >
        返回列表
      </el-button>
    </div>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      class="recipe-form"
    >
      <el-form-item label="菜谱名称" prop="name">
        <el-input
          v-model="formData.name"
          placeholder="请输入菜谱名称"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="菜谱描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          placeholder="请输入菜谱描述"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="菜谱图片">
        <el-upload
          class="avatar-uploader"
          name="imageFile"
          :action="uploadAction"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleImageSuccess"
          :before-upload="beforeAvatarUpload"
        >
          <el-image v-if="isRenderableRecipeImage(formData.imageUrl)" :src="formData.imageUrl" class="avatar" />
          <el-icon v-else class="avatar-uploader-icon">
            <Plus />
          </el-icon>
        </el-upload>
        <div class="upload-hint">支持 JPG、PNG、GIF；大图会自动压缩至适合网页显示的尺寸</div>
      </el-form-item>

      <el-form-item label="适宜人群" prop="suitableCrowd">
        <el-input
          v-model="formData.suitableCrowd"
          placeholder="如：高血压患者、糖尿病患者、通用等"
          maxlength="100"
        />
      </el-form-item>

      <el-form-item label="营养信息" prop="nutritionInfo">
        <el-input
          v-model="formData.nutritionInfo"
          type="textarea"
          placeholder="请输入营养信息"
          :rows="2"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="烹饪步骤" prop="cookingSteps">
        <el-input
          v-model="formData.cookingSteps"
          type="textarea"
          placeholder="请输入烹饪步骤，每步骤一行"
          :rows="4"
          maxlength="1000"
          show-word-limit
        />
        <div class="hint">提示：每行一个步骤，系统会自动编号</div>
      </el-form-item>

      <el-form-item label="小贴士" prop="tips">
        <el-input
          v-model="formData.tips"
          type="textarea"
          placeholder="请输入小贴士"
          :rows="2"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '更新菜谱' : '创建菜谱' }}
        </el-button>
        <el-button @click="resetForm">重置</el-button>
        <el-button @click="goBack">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { getRecipeDetail, saveRecipe, updateRecipe } from '@/api/recipe'
import { isRenderableRecipeImage } from '@/utils/media'
import { prepareImageUpload } from '@/utils/upload'

const route = useRoute()
const router = useRouter()

const formRef = ref()
const formData = reactive({
  id: null,
  name: '',
  description: '',
  imageUrl: '',
  suitableCrowd: '',
  nutritionInfo: '',
  cookingSteps: '',
  tips: ''
})
const submitting = ref(false)

// 判断是编辑还是新增
const isEdit = computed(() => {
  return route.params.id && route.params.id !== '0'
})

// 图片上传地址（后端接口）
const uploadAction = '/api/upload/image'
const uploadToken = sessionStorage.getItem('token') || localStorage.getItem('token')
const uploadHeaders = uploadToken ? { Authorization: `Bearer ${uploadToken}` } : {}

// 表单验证规则
const formRules = reactive({
  name: [
    { required: true, message: '请输入菜谱名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入菜谱描述', trigger: 'blur' },
    { min: 5, max: 500, message: '长度在 5 到 500 个字符', trigger: 'blur' }
  ]
})

// 图片上传成功回调
const handleImageSuccess = (response) => {
  // 根据后端实际返回结构调整
  if (response && response.code === 200) {
    formData.imageUrl = response.result
    ElMessage.success('图片上传成功')
  } else if (response && response.data && response.data.code === 200) {
    // 兼容嵌套结构
    formData.imageUrl = response.data.result
    ElMessage.success('图片上传成功')
  } else {
    const errorMsg = response?.msg || response?.message || '图片上传失败'
    ElMessage.error(errorMsg)
  }
}

// 图片上传前校验
const beforeAvatarUpload = async (file) => {
  try {
    return await prepareImageUpload(file)
  } catch (error) {
    ElMessage.error(error.message || '图片处理失败')
    return false
  }
}

// 加载菜谱详情（编辑时）
const loadRecipeDetail = async (id) => {
  try {
    const response = await getRecipeDetail(id)

    let res = response
    if (response && response.data && response.data.code !== undefined) {
      res = response.data
    }

    if (res && res.code === 200) {
      Object.assign(formData, res.result)
      ElMessage.success('菜谱数据加载成功')
    } else {
      const errorMsg = res?.msg || '获取菜谱详情失败'
      console.error('【菜谱编辑】获取菜谱详情失败:', errorMsg)
      ElMessage.error(errorMsg)
      goBack()
    }
  } catch (error) {
    console.error('【菜谱编辑】获取菜谱详情失败:', error)
    ElMessage.error('获取菜谱详情失败')
    goBack()
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) {
      ElMessage.warning('请填写完整的菜谱信息')
      return
    }

    try {
      submitting.value = true


      if (isEdit.value) {
        const response = await updateRecipe(formData)
        let res = response
        if (response && response.data && response.data.code !== undefined) {
          res = response.data
        }

        if (res && res.code === 200) {
          ElMessage.success('菜谱更新成功')
          goBack()
        } else {
          const errorMsg = res?.msg || '更新失败'
          ElMessage.error(errorMsg)
        }
      } else {
        const response = await saveRecipe(formData)
        let res = response
        if (response && response.data && response.data.code !== undefined) {
          res = response.data
        }

        if (res && res.code === 200) {
          ElMessage.success('菜谱创建成功')
          goBack()
        } else {
          const errorMsg = res?.msg || '创建失败'
          ElMessage.error(errorMsg)
        }
      }
    } catch (error) {
      console.error('【菜谱编辑】保存菜谱失败:', error)
      ElMessage.error('保存失败，请重试')
    } finally {
      submitting.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  if (isEdit.value) {
    loadRecipeDetail(route.params.id)
  } else {
    formData.id = null
    formData.name = ''
    formData.description = ''
    formData.imageUrl = ''
    formData.suitableCrowd = ''
    formData.nutritionInfo = ''
    formData.cookingSteps = ''
    formData.tips = ''

    if (formRef.value) {
      formRef.value.clearValidate()
    }
  }
}

// 返回列表
const goBack = () => {
  router.push('/RecipeManageView')
}

// 生命周期
onMounted(() => {
  if (isEdit.value) {
    loadRecipeDetail(route.params.id)
  } else {
    formData.suitableCrowd = '通用'
  }
})
</script>

<style lang="scss" scoped>
.recipe-edit-container {
  padding: 20px;
  min-height: calc(100vh - 100px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #ebeef5;

  h1 {
    font-size: 1.8rem;
    color: #333;
    font-weight: 600;
    margin: 0;
  }

  .back-btn {
    margin-right: 10px;
  }
}

.recipe-form {
  max-width: 800px;
  margin: 0 auto;
  padding: 30px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

/* 图片上传组件样式（参考活动管理） */
.avatar-uploader {
  width: 100%;
  display: block;
}
.avatar {
  width: 200px;
  height: 150px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid #dcdfe6;
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
  width: 200px;
  height: 150px;
  text-align: center;
  line-height: 150px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
}
.upload-hint {
  font-size: 0.8rem;
  color: #909399;
  margin-top: 8px;
}

.hint {
  font-size: 0.8rem;
  color: #909399;
  margin-top: 5px;
}

@media (max-width: 768px) {
  .recipe-form {
    padding: 20px 15px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;

    h1 {
      font-size: 1.5rem;
    }
  }

  .avatar, .el-icon.avatar-uploader-icon {
    width: 150px;
    height: 120px;
    line-height: 120px;
  }
}
</style>
