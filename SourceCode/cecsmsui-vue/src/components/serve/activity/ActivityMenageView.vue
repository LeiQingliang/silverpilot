<template>
    <el-card class="box-card">
        <template #header>
            <div class="card-header">
                <span>活动管理</span>
                <div class="toolbar-actions">
                    <div class="mt-4">
                        <el-input v-model="aName" placeholder="请输入关键字" class="input-with-select">
                            <template #append>
                                <el-button @click="btn_loadbyname" :icon="Search" />
                            </template>
                        </el-input>
                    </div>
                    <el-button type="success" :icon="Plus" @click="btn_add">新增</el-button>
                </div>
            </div>
        </template>
        <el-config-provider :locale="locale">

            <!-- 表格 -->
            <el-table class="desktop-activity-table" :data="tableData" height="400" :row-class-name="tableRowClassName">
                <el-table-column prop="id" label="活动编号" width="90px" fixed />
                <el-table-column prop="activityName" label="活动名称" width="200px" fixed />
                <el-table-column prop="image" label="活动图片" width="150">
                    <template #default="scope">
                        <el-image class="activity-thumbnail" :src="scope.row.image"
                            :preview-src-list="[scope.row.image]" />
                    </template>
                </el-table-column>
                <el-table-column prop="activityType" label="活动类别" width="120px">
                    <template #default="scope">
                        {{
                            scope.row.activityType?.type || '--'
                        }}
                    </template>
                </el-table-column>
                <el-table-column prop="activityDate" label="活动日期" sortable width="150px" />
                <el-table-column prop="startTime" label="开始时间" sortable width="120px" />
                <el-table-column prop="endTime" label="结束时间" sortable width="120px" />
                <el-table-column prop="activityAddress" label="活动地点" width="200px" />
                <el-table-column prop="dId" label="负责人" width="75px">
                    <template #default="scope">
                        {{
                            scope.row.director?.name || '--'
                        }}
                    </template>
                </el-table-column>
                <el-table-column prop="dId" label="联系电话" width="150px">
                    <template #default="scope">
                        {{
                            scope.row.director?.telephone || '--'
                        }}
                    </template>
                </el-table-column>
                <el-table-column prop="limitNum" label="报名人数限制" width="120px" />
                <el-table-column prop="signNum" label="已报名人数" width="120px" />
                <el-table-column prop="activityPoint" label="活动积分" />
                <el-table-column prop="state" label="状态" :filters="[
                    { text: '已取消', value: 0 },
                    { text: '未开始', value: 1 },
                    { text: '报名中', value: 2 },
                    { text: '已结束', value: 3 },
                ]" :filter-method="filterState" filter-placement="bottom-end" width="110px">
                    <template #default="scope">
                        <el-tag :type="getActivityState(scope.row.state).type" effect="light">
                            {{ getActivityState(scope.row.state).label }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" fixed="right" width="180px">
                    <template #default="scope">
                        <el-button size="small" type="primary"
                            @click="handleEdit(scope.$index, scope.row)">编辑</el-button>
                            <el-button size="small" type="primary"
                            @click="handleDel(scope.row)">删除</el-button>
                        <el-button size="small" type="primary" @click="toTable(scope.row)">报名表</el-button>
                    </template>
                </el-table-column>

            </el-table>
            <div class="mobile-activity-list" aria-label="活动管理列表">
                <article v-for="activity in tableData" :key="activity.id">
                    <header><div><small>ACTIVITY {{ activity.id }}</small><h2>{{ activity.activityName || '未命名活动' }}</h2></div><el-tag effect="plain">{{ activity.activityType?.type || '未分类' }}</el-tag></header>
                    <dl>
                        <div><dt>活动时间</dt><dd>{{ activity.activityDate || '待定' }} {{ activity.startTime || '' }} – {{ activity.endTime || '' }}</dd></div>
                        <div><dt>活动地点</dt><dd>{{ activity.activityAddress || '待定' }}</dd></div>
                        <div><dt>负责人</dt><dd>{{ activity.director?.name || '待安排' }} {{ activity.director?.telephone || '' }}</dd></div>
                        <div><dt>报名进度</dt><dd>{{ activity.signNum || 0 }} / {{ activity.limitNum || '不限' }}</dd></div>
                    </dl>
                    <footer><el-button type="primary" plain @click="handleEdit(0, activity)">编辑</el-button><el-button type="danger" plain @click="handleDel(activity)">删除</el-button><el-button type="primary" @click="toTable(activity)">查看报名表</el-button></footer>
                </article>
                <el-empty v-if="!tableData.length" description="暂无活动数据" :image-size="64" />
            </div>
            <ActivityDialog />
            <SignedDialog />
            <!-- 分页 -->
            <div class="table-pagination">
                <el-pagination v-model:current-page="currentPage" :page-size="3" :total="total" :pager-count="5"
                    :background="background" layout="total, prev, pager, next, jumper" @current-change="handleChange" />
            </div>
        </el-config-provider>
    </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import { Plus, Search } from '@element-plus/icons-vue'
import ActivityDialog from './ActivityDialog.vue'
import SignedDialog from './SignedDialog.vue'
//pinia
import { storeToRefs } from 'pinia'
import { useActivityStore } from "../../../stores/activity"
import { useMyactivityStore } from "../../../stores/myactivity.js";
const activityStore = useActivityStore()
const myactivityStore = useMyactivityStore()

//分页控制
const background = ref(true)
const { aName, tableData, currentPage, total } = storeToRefs(activityStore)
const handleChange = (currentPage) => {
    activityStore.currentPage = currentPage
    activityStore.selectAllByPage()
}
activityStore.selectAllByPage()

//搜索
const btn_loadbyname = () => {
    activityStore.loadbyname()
}

const btn_add = () => {
    activityStore.preInfo4Add()
}

const handleEdit = (index, row) => {
    activityStore.preInfo4Edit(index, row)
}

const handleDel = (row) => {
    activityStore.deleteAction(row)
}
//报名表
const toTable = (row) => {
    myactivityStore.pre4UserList(row)
}

//语言
const language = ref('zh-cn')
const locale = computed(() => (language.value === 'zh-cn' ? zhCn : en))

//斑马条
const tableRowClassName = ({ rowIndex }) => {
    return rowIndex % 2 === 1 ? 'success-row' : ''
}

const activityStates = Object.freeze({
    0: { label: '已取消', type: 'danger' },
    1: { label: '未开始', type: 'warning' },
    2: { label: '报名中', type: 'success' },
    3: { label: '已结束', type: 'info' }
})
const getActivityState = (state) => activityStates[Number(state)] ?? { label: '未知', type: 'info' }
const filterState = (value, row) => Number(row.state) === value

</script>

<style scoped>
.box-card {
    width: 99%;
    height: 94%;
    /* margin-top: 12px; */
    border-radius: 12px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.text {
    font-size: 14px;
}

/* .item {
    margin-bottom: 18px;
} */

.el-descriptions {
    margin-top: 20px;
}

.cell-item {
    display: flex;
    align-items: center;
}

.margin-top {
    margin-top: 5px;
}

.tagclass {
    margin-left: 5px;
    color: black;
    border: none;
}

.input-with-select .el-input-group__prepend {
    background-color: var(--el-fill-color-blank);
}

.mobile-activity-list { display: none; }
.desktop-activity-table { width: 100%; }
.activity-thumbnail { width: 100%; height: 92px; }

@media (max-width: 760px) {
    .desktop-activity-table { display: none; }
    .mobile-activity-list { display: grid; gap: 12px; }
    .mobile-activity-list article { padding: 16px; color: var(--sp-text); background: var(--sp-surface-raised); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-xs); }
    .mobile-activity-list header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
    .mobile-activity-list header small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
    .mobile-activity-list h2 { margin: 6px 0 0; font-size: 18px; line-height: 1.35; }
    .mobile-activity-list dl { display: grid; gap: 7px; margin: 14px 0; padding: 12px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-sm); }
    .mobile-activity-list dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; font-size: 12px; line-height: 1.5; }
    .mobile-activity-list dt { color: var(--sp-text-muted); }.mobile-activity-list dd { min-width: 0; margin: 0; color: var(--sp-text-secondary); overflow-wrap: anywhere; }
    .mobile-activity-list footer { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; padding-top: 12px; border-top: 1px solid var(--sp-border); }
    .mobile-activity-list footer .el-button { width: 100%; margin: 0; }
    .mobile-activity-list footer .el-button:last-child { grid-column: 1 / -1; }
}
</style>

<style>
.el-table .success-row {
    --el-table-tr-bg-color: #c6e2ff
}
</style>
