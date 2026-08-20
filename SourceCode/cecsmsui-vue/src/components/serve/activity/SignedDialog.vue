<template>
    <el-dialog width="50%" v-model="dialogVisible" :title="dialogTitle" :before-close="handleClose" draggable overflow
        center>
        <el-table :data="UserList" height="400" border>
            <el-table-column prop="name" label="姓名" />
            <el-table-column prop="sex" label="性别">
                <template #default="scope">
                    {{ scope.row.sex === '1' || scope.row.sex === '男' ? '男' : '女' }}
                </template>
            </el-table-column>
            <el-table-column prop="age" label="年龄" />
            <el-table-column prop="telephone" label="手机号" />
            <el-table-column prop="qiandao" label="签到" />
        </el-table>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="canelDialog">取消</el-button>
                <el-button type="primary" @click="toDownload" :icon="Promotion"> 下载 </el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import $axios from '../../../utils/axios.js';
import { storeToRefs } from "pinia";
import { useMyactivityStore } from "../../../stores/myactivity.js";
import { Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { saveBlobResponse } from '../../../utils/download.js'
const myactivityStore = useMyactivityStore()
const { UserList, dialogVisible, dialogTitle } = storeToRefs(myactivityStore)

const canelDialog = () => {
    myactivityStore.dialogVisible = false
    myactivityStore.UserList = []
}
const handleClose = () => {
    myactivityStore.dialogVisible = false
    myactivityStore.UserList = []
}

const toDownload = async () => {
    try {
        const path = '/download/excel/' + myactivityStore.aId;
        const response = await $axios.post(path, null, {
            responseType: 'blob'
        });

        if (response.status === 200) {
            await saveBlobResponse(response, myactivityStore.dialogTitle + '.xlsx')
        } else {
            ElMessage.error('下载失败，请稍后重试')
        }
    } catch (error) {
        ElMessage.error('下载失败，请检查网络或登录权限')
    }
}
</script>
