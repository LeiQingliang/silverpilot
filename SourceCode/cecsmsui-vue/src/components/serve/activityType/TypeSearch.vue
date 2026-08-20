<template>
    <div class="mt-4">
        <el-input v-model="type" placeholder="请输入活动类型" class="input-with-select">
            <template #append>
                <el-button @click="btn_loadbyname" :icon="Search" />
            </template>
        </el-input>
    </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import { storeToRefs } from "pinia";
import { useActivitytypeStore } from '../../../stores/activitytype'
const activitytypeStore = useActivitytypeStore()
const { type } = storeToRefs(activitytypeStore)
const btn_loadbyname = () => {
    activitytypeStore.loadByName()
}
</script>

<style>
.input-with-select .el-input-group__prepend {
    background-color: var(--el-fill-color-blank);
}
</style>