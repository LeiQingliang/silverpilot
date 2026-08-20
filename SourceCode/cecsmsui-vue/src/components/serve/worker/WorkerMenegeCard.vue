<template>
    <el-card class="box-card">
        <template #header>
            <div class="card-header">
                <span>社区工作者管理</span>
                <div class="toolbar-actions">
                    <el-input v-model="searchName" placeholder="请输入工作者姓名" class="input-with-select">
                        <template #append>
                            <el-button @click="btn_loadbyname" :icon="Search" />
                        </template>
                    </el-input>
                    <el-button type="success" :icon="Plus" @click="btn_add">添加工作者</el-button>
                </div>
            </div>
        </template>
        <WorkerMenageTable />
        <AddWokerDialog />
    </el-card>
</template>

<script setup>
import WorkerMenageTable from './WorkerMenageTable.vue'
import AddWokerDialog from './AddWokerDialog.vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { storeToRefs } from "pinia";
import { useUserStore } from "../../../stores/user.js";
const userStore = useUserStore()
const { searchName } = storeToRefs(userStore)

const btn_loadbyname = () => {
    userStore.roleId = 2
    userStore.selectByNameOrIdNum()
}

const btn_add = () => {
    userStore.pre4Add();
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
