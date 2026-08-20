<template>
    <el-card class="box-card">
        <template #header>
            <div class="card-header">
                <span>用户管理</span>
                <div class="toolbar-actions">
                    <el-input v-model="searchName" placeholder="请输入用户姓名" class="input-with-select">
                        <template #append>
                            <el-button @click="btn_loadbyname" :icon="Search" />
                        </template>
                    </el-input>
                    <!-- 恢复添加按钮 -->
                    <el-button type="success" :icon="Plus" @click="btn_add">添加新用户</el-button>
                </div>
            </div>
        </template>
        <UserMenageTable />
        <AddUserDialog />
    </el-card>
</template>

<script setup>
import { Search, Plus } from '@element-plus/icons-vue'
import UserMenageTable from './UserMenageTable.vue';
import AddUserDialog from './AddUserDialog.vue';  // 引入弹窗组件
import { storeToRefs } from "pinia";
import { useUserStore } from "../../../stores/user.js";
const userStore = useUserStore()
const { searchName } = storeToRefs(userStore)

const btn_loadbyname = () => {
    userStore.roleId = 4
    userStore.selectByNameOrIdNum()
}

const btn_add = () => {
    userStore.pre4Add();  // 打开新增弹窗
}
</script>

<style scoped>
.box-card {
    width: 99%;
    height: 94%;
    margin-top: 12px;
    border-radius: 12px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.input-with-select .el-input-group__prepend {
    background-color: var(--el-fill-color-blank);
}
</style>
