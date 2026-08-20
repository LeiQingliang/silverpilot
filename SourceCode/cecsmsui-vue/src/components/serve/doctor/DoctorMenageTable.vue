<template>
    <el-config-provider :locale="locale">
        <el-table class="desktop-people-table" :data="userData" height="450" style="width: 100%" :table-layout="tableLayout"
            :row-class-name="tableRowClassName">
            <el-table-column prop="id" label="人员编号" width="100px" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="name" label="姓名" />
            <el-table-column prop="sex" label="性别" />
            <el-table-column prop="birthday" label="出生日期" width="120" />
            <el-table-column prop="age" label="年龄" width="80" />
            <el-table-column prop="telephone" label="联系电话" />
            <el-table-column prop="address" label="现住址" show-overflow-tooltip />
            <el-table-column label="操作">
                <template #default="scope">
                    <!-- 新增修改按钮 -->
                    <el-button size="small" type="primary" @click="editDoctor(scope.row)">修改</el-button>
                    <el-popconfirm width="320" confirm-button-text="确定" cancel-button-text="取消" :icon="InfoFilled"
                        icon-color="#626AEF" :title="'确定要注销吗?'" @confirm="logout(scope.row)">
                        <template #reference>
                            <el-button size="small" type="danger">注销</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>
        <PeopleMobileList :items="userData" entity-label="医护人员" @edit="editDoctor" @deactivate="logout" />
    </el-config-provider>
</template>

<script setup>
import { ref, computed } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import PeopleMobileList from '../common/PeopleMobileList.vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
const language = ref('zh-cn')
const locale = computed(() => (language.value === 'zh-cn' ? zhCn : en))
const tableLayout = 'auto'
const tableRowClassName = ({ rowIndex }) => rowIndex % 2 === 1 ? 'success-row' : ''
import { storeToRefs } from "pinia";
import { useUserStore } from "../../../stores/user.js";
const userStore = useUserStore()
const { userData } = storeToRefs(userStore)
userStore.getAllDoctors()

const logout = (row) => userStore.logout(row)

// 新增修改方法
const editDoctor = (row) => {
    // 调用 store 中的方法，将当前行数据传入，打开编辑弹窗
    userStore.pre4Edit(row)
}
</script>

<style scoped>
@media (max-width: 760px) { .desktop-people-table { display: none; } }
</style>
