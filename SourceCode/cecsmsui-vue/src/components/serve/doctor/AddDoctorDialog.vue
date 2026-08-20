<template>
    <el-dialog
        width="35%"
        v-model="dialogVisible"
        :title="userStore.isEdit ? '修改医护工作者' : '添加医护工作者'"
        :before-close="handleClose"
        draggable
        center
        align-center
        append-to-body
        :z-index="3000"
        :lock-scroll="true"
        :close-on-click-modal="false"
    >
        <el-form :model="formData" ref="ruleFormRef" :rules="rules" status-icon>
            <el-form-item label="用户名" prop="username" label-width="30%">
                <el-input v-model="formData.username" class="input" placeholder="输入用户名" style="width: 70%" />
            </el-form-item>
            <el-form-item v-if="!isEdit" label="密码" prop="password" label-width="30%">
                <el-input type="password" v-model="formData.password" placeholder="8-18位字母、数字或“_”，字母开头" class="input"
                    show-password style="width: 70%" />
            </el-form-item>
            <el-form-item label="姓名" prop="name" label-width="30%">
                <el-input v-model="formData.name" class="input" placeholder="请输入姓名" style="width: 70%" />
            </el-form-item>
            <el-form-item label="性别" prop="sex" label-width="30%">
                <el-input v-model="formData.sex" class="input" placeholder="请输入性别" style="width: 70%" />
            </el-form-item>
            <el-form-item label="手机号码" prop="telephone" label-width="30%">
                <el-input v-model="formData.telephone" class="input" placeholder="请输入11位手机号" style="width: 70%" />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button type="primary" @click="onSubmit(ruleFormRef)">
                    {{ userStore.isEdit ? '保存修改' : '注册' }}
                </el-button>
                <el-button @click="resetForm(ruleFormRef)">重置</el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { storeToRefs } from "pinia";
import { useUserStore } from "../../../stores/user.js";
const userStore = useUserStore()
const { formData, dialogVisible, isEdit } = storeToRefs(userStore)

const ruleFormRef = ref()
const onSubmit = async (formEl) => {
    if (!formEl) return
    await formEl.validate(async (valid) => {
        if (valid) {
            // 确保 roleId 为 3（医护人员）
            userStore.formData.roleId = 3
            // 统一调用 submitUser，内部根据 isEdit 决定新增或更新
            await userStore.submitUser()
        }
    })
}

const resetForm = (formEl) => {
    if (!formEl) return
    formEl.resetFields()
}

const handleClose = (done) => {
    // 关闭前可以做一些清理，比如重置表单
    done()
}

const rules = reactive({
    username: [
        {
            required: true,
            message: '请输入用户名',
            trigger: 'blur'
        },
        {
            min: 5,
            max: 10,
            message: '5-10个数字或字母组合',
            trigger: 'blur'
        }
    ],
    name: [
        {
            required: true,
            message: '请输入真实姓名',
            trigger: 'blur'
        }
    ],
    sex: [
        {
            required: true,
            message: '请选择性别',
            trigger: 'change'
        }
    ],
    telephone: [
        {
            required: true,
            message: '请输入手机号',
            trigger: 'blur'
        },
        {
            pattern: /^1[3456789]\d{9}$/,
            message: '手机号码格式不正确',
            trigger: 'change'
        }
    ],
    password: [
        {
            required: true,
            message: '请输入密码',
            trigger: 'blur'
        },
        {
            pattern: /^[a-zA-Z]\w{7,17}$/,
            message: '只能包含字母、数字、下划线,以字母开头',
            trigger: 'change'
        },
        {
            min: 8,
            max: 18,
            message: '8-18个字符',
            trigger: 'blur'
        }
    ],
})
</script>
