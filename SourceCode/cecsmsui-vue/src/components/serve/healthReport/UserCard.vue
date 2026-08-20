<template>
    <el-card class="user-card">
        <template #header>
            <div class="card-header">
                <el-input v-model="searchName" placeholder="请输入姓名或身份证号" class="input-with-select">
                    <template #append>
                        <el-button @click="btn_loadbyname" :icon="Search" />
                    </template>
                </el-input>
            </div>
        </template>
        <el-table class="desktop-health-users" :data="userData" style="width: 100%;margin-left: auto;margin-right: auto;" :table-layout="tableLayout"
            :row-class-name="tableRowClassName" height="315">
            <el-table-column prop="id" label="序号" width="64" />
            <el-table-column prop="name" label="姓名" />
            <el-table-column prop="sex" label="性别" />
            <el-table-column prop="age" label="年龄" />
            <el-table-column prop="telephone" label="联系电话" width="140" />
            <el-table-column prop="idNum" label="身份证号" width="190" />
            <el-table-column prop="address" label="家庭住址" width="190" />
            <el-table-column label="操作" fixed="right" width="130">
                <template #default="scope">
                    <el-button size="small" type="primary" @click="toReport(scope.row)">查看档案</el-button>
                </template>
            </el-table-column>

        </el-table>
        <div class="mobile-health-users" aria-label="健康档案用户列表">
            <article v-for="user in userData" :key="user.id">
                <header><div><small>RESIDENT {{ user.id }}</small><h3>{{ user.name || '未命名用户' }}</h3></div><el-tag effect="plain">{{ user.sex || '未知' }}</el-tag></header>
                <dl><div><dt>年龄</dt><dd>{{ user.age || '--' }}</dd></div><div><dt>联系电话</dt><dd>{{ user.telephone || '--' }}</dd></div></dl>
                <el-button type="primary" plain @click="toReport(user)">查看健康档案</el-button>
            </article>
            <el-empty v-if="!userData.length" description="暂无居民数据" :image-size="64" />
        </div>
        <el-config-provider :locale="locale">
            <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="total"
                :pager-count="5" background layout="prev, pager, next, jumper,total"
                @current-change="handleCurrentChange" style="width: max-content;margin-top: 15px" />

        </el-config-provider>
    </el-card>
</template>

<script setup>
import { storeToRefs } from "pinia";
import { useUserStore } from "../../../stores/user.js";
const userStore = useUserStore()
const { userData, currentPage, total, searchName } = storeToRefs(userStore)
const tableLayout = 'auto'
const tableRowClassName = ({ rowIndex }) => rowIndex % 2 === 1 ? 'success-row' : ''

const btn_loadbyname = () => {
    userStore.selectByNameOrIdNum()
}
const pageSize = ref(6)
const handleCurrentChange = () => {
    userStore.pageSize = pageSize.value
    userStore.getUsers()
}
handleCurrentChange()

import { useReportStore } from "../../../stores/report.js";
const reportStore = useReportStore()
const toReport = (row) => {
    reportStore.uId = row.id
    reportStore.selectReport(row.id)
}


import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
//语言配置
const language = ref('zh-cn')
const locale = computed(() => (language.value === 'zh-cn' ? zhCn : en))
</script>

<style scoped>
.user-card { width: 100%; min-width: 0; }
.input-with-select .el-input-group__prepend {
    background-color: var(--el-fill-color-blank);
}
.mobile-health-users { display: none; }
@media (max-width: 760px) {
    .desktop-health-users { display: none; }
    .mobile-health-users { display: grid; gap: 10px; }
    .mobile-health-users article { padding: 15px; color: var(--sp-text); background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); }
    .mobile-health-users header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
    .mobile-health-users small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
    .mobile-health-users h3 { margin: 5px 0 0; font-size: 17px; }
    .mobile-health-users dl { display: grid; gap: 6px; margin: 12px 0; }
    .mobile-health-users dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; font-size: 12px; }
    .mobile-health-users dt { color: var(--sp-text-muted); }.mobile-health-users dd { margin: 0; color: var(--sp-text-secondary); overflow-wrap: anywhere; }
    .mobile-health-users .el-button { width: 100%; }
}
</style>
