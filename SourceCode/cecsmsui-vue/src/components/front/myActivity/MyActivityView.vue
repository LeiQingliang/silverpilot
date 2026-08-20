<template>
    <el-tabs type="border-card" class="demo-tabs">
        <el-tab-pane label="全部">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header2" width="100%" style="text-align:left">
                        <span>活动详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="allData" style="width: 100%" max-height="430">
                        <el-table-column label="活动名称" prop="activityName" />
                        <el-table-column label="活动类别" prop="type" />
                        <el-table-column label="地点" prop="activityAddress" />
                        <el-table-column label="时间" prop="routes">
                            <template #default="scope">
                                {{ scope.row.activityDate }} {{ scope.row.startTime }}
                            </template>
                        </el-table-column>
                        <el-table-column label="负责人" prop="name" />
                        <el-table-column label="联系电话" prop="telephone" />
                        <el-table-column label="活动积分" prop="activityPoint" />
                        <el-table-column label="订单状态" prop="myState" />
                        <el-table-column label="操作" fixed="right" width="110px">
                            <template #default="scope">
                                <el-popconfirm
                                    v-if="scope.row.myState === '报名成功' && scope.row.aState !== '已结束'"
                                    width="180" confirm-button-text="确定" cancel-button-text="取消"
                                    :icon="InfoFilled" icon-color="#626AEF" :title="'确定要取消报名吗?'"
                                    @confirm="toCancel(scope.row)">
                                    <template #reference>
                                        <el-button size="small" type="primary">取消报名</el-button>
                                    </template>
                                </el-popconfirm>
                                <el-button v-if="scope.row.aState == '已结束'" size="small" type="primary"
                                    disabled>活动已结束</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                    <MobileActivityRecords :items="allData" cancellable @cancel="toCancel" />
                </div>
            </el-card>
        </el-tab-pane>
        <el-tab-pane label="未开始">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header" width="100%" style="text-align:left">
                        <span>活动详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="futureData" style="width: 100%" max-height="430">
                        <el-table-column label="活动名称" prop="activityName" width="180" />
                        <el-table-column label="活动类别" prop="type" />
                        <el-table-column label="地点" prop="activityAddress" />
                        <el-table-column label="时间" prop="routes">
                            <template #default="scope">
                                {{ scope.row.activityDate }} {{ scope.row.startTime }}
                            </template>
                        </el-table-column>
                        <el-table-column label="负责人" prop="name" />
                        <el-table-column label="联系电话" prop="telephone" />
                        <el-table-column label="活动积分" prop="activityPoint" />
                        <el-table-column label="订单状态" prop="myState" />
                        <el-table-column label="操作" fixed="right" width="110px">
                            <template #default="scope">
                                <el-popconfirm width="180" confirm-button-text="确定" cancel-button-text="取消"
                                    :icon="InfoFilled" icon-color="#626AEF" :title="'确定要取消报名吗?'"
                                    @confirm="toCancel(scope.row)">
                                    <template #reference>
                                        <el-button size="small" type="primary"
                                            :disabled="scope.row.myState === '已取消报名' ? true : false">取消报名</el-button>
                                    </template>
                                </el-popconfirm>
                            </template>
                        </el-table-column>
                    </el-table>
                    <MobileActivityRecords :items="futureData" cancellable @cancel="toCancel" />
                </div>
            </el-card>
        </el-tab-pane>
        <el-tab-pane label="已取消报名">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header2" width="100%" style="text-align:left">
                        <span>活动详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="canceledData" style="width: 100%" max-height="430">
                        <el-table-column label="活动名称" prop="activityName" width="180" />
                        <el-table-column label="活动类别" prop="type" />
                        <el-table-column label="地点" prop="activityAddress" />
                        <el-table-column label="时间" prop="routes">
                            <template #default="scope">
                                {{ scope.row.activityDate }} {{ scope.row.startTime }}
                            </template>
                        </el-table-column>
                        <el-table-column label="负责人" prop="name" />
                        <el-table-column label="联系电话" prop="telephone" />
                        <el-table-column label="活动积分" prop="activityPoint" />
                    </el-table>
                    <MobileActivityRecords :items="canceledData" />
                </div>
            </el-card>
        </el-tab-pane>
    </el-tabs>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'
import { storeToRefs } from "pinia";
import { useMyactivityStore } from "../../../stores/myactivity.js";
import MobileActivityRecords from './MobileActivityRecords.vue'
const myactivityStore = useMyactivityStore()
const { futureData, canceledData, allData } = storeToRefs(myactivityStore)
myactivityStore.init()
myactivityStore.getFutureData()
myactivityStore.getCanceledData()

const toCancel = (row) => myactivityStore.update(row)
</script>

<style scoped>
.demo-tabs {
    height: 85vh;
}

.box-card {
    height: 100%;
}

@media (max-width: 760px) {
    .demo-tabs { height: auto; min-height: 480px; }
    .desktop-record-table { display: none; }
    .box-card { height: auto; }
    .box-card :deep(.el-card__body) { padding: 12px; }
}
</style>
