<template>
    <el-tabs type="border-card" class="demo-tabs">
        <el-tab-pane label="全部">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header2" width="100%" style="text-align:left">
                        <span>订单详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="myOrder" style="width: 100%" max-height="430">
                        <!-- 表格 -->
                        <!-- 展开行内容 -->
                        <el-table-column type="expand" width="56">
                            <template #default="props">
                                <el-descriptions class="descriptions" :column="3" style="width: 85%" border>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                联系方式
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.name || '未知用户' }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.telephone || '暂无联系方式' }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.reserveDate">{{
                                            props.row.reserveDate }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务地点
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.serviceAddress">{{
                                            props.row.serviceAddress }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                下单时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderDate }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderTime }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                受理时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.orderState === 2">{{
                                            props.row.acceptDate }}</el-tag>
                                        <el-tag style="margin-right: 2px" v-if="props.row.orderState === 2"> {{
                                            props.row.acceptTime }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class=" cell-item">
                                                完成时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 5px" v-if="props.row.orderState === 3">{{
                                            props.row.finishDate }}</el-tag>
                                        <el-tag style="margin-right: 5px" v-if="props.row.orderState === 3">{{
                                            props.row.finishTime }}</el-tag>
                                    </el-descriptions-item>
                                </el-descriptions>
                            </template>
                        </el-table-column>

                        <el-table-column prop="typeId" label="服务类别" width="190">
                            <template #default="scope">
                                {{ scope.row.typeB?.serviceName || '未分类' }}/{{ scope.row.typeS?.serviceName || '未分类' }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDetail" label="订单详情" width="160" />
                        <el-table-column prop="manager" label="受理人" width="180">
                            <template #default="scope">
                                <p v-if="scope.row.manager">{{ scope.row.manager.name }} {{ scope.row.manager.telephone
                                    }}</p>
                            </template>
                        </el-table-column>
                        <el-table-column prop="fff" label="服务人员" width="170">
                            <template #default="scope">
                                {{ scope.row.name }} {{ scope.row.telephone }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDate" label="下单时间" sortable width="130" />
                        <el-table-column prop="orderState" label="订单状态">
                            <template #default="scope">
                                <p v-if="scope.row.orderState == '0'" style="color: #ffa500">待受理</p>
                                <p v-if="scope.row.orderState == '1'" style="color: #ff0000">已取消</p>
                                <p v-if="scope.row.orderState == '2'" style="color: #67c23a">待完成</p>
                                <p v-if="scope.row.orderState == '3'" style="color: #FF4500">待评分</p>
                                <p v-if="scope.row.orderState == '4'" style="color: #67c23a">已完成</p>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" fixed="right" width="110">
                            <template #default="scope">
                                <el-popconfirm v-if="scope.row.orderState == '0'" width="180" confirm-button-text="确定" cancel-button-text="取消"
                                    :icon="InfoFilled" icon-color="#626AEF" :title="'确定要取消预约吗?'"
                                    @confirm="toCancel(scope.row)">
                                    <template #reference>
                                        <el-button size="small" type="primary">取消预约</el-button>
                                    </template>
                                </el-popconfirm>
                                <el-button v-if="scope.row.orderState == '1'" size="small" type="primary"
                                    disabled>已&ensp;取&ensp;消</el-button>
                                <el-popconfirm v-if="scope.row.orderState == '2'" width="200" confirm-button-text="确定" cancel-button-text="取消"
                                    :icon="InfoFilled" icon-color="#626AEF" :title="'确定要确认完成服务吗?'"
                                    @confirm="toConfirm(scope.row)">
                                    <template #reference>
                                        <el-button size="small" type="primary">确认完成</el-button>
                                    </template>
                                </el-popconfirm>
                                <el-popover v-if="scope.row.orderState == '3'" placement="bottom" :width="100" trigger="click"
                                    v-model:visible="scope.row.popVisible">
                                    <template #reference>
                                        <el-button size="small" type="primary"
                                            @click="showPopover(scope.row)">&emsp;评分&emsp;</el-button>
                                    </template>
                                    <el-rate v-model="scope.row.rate" allow-half clearable
                                        @change="toRate(scope.row)" />
                                </el-popover>
                                <el-button v-if="scope.row.orderState == '4'" size="small" type="primary"
                                    disabled>服务已完成</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                    <MobileServiceOrders :items="myOrder" @cancel="toCancel" @confirm="toConfirm" @rate="toRate" />
                </div>
            </el-card>
        </el-tab-pane>
        <el-tab-pane label="待受理">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header2" width="100%" style="text-align:left">
                        <span>订单详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="noAcceptData" style="width: 100%" max-height="430">
                        <!-- 展开行内容 -->
                        <el-table-column type="expand" width="56">
                            <template #default="props">
                                <el-descriptions class="descriptions" :column="3" style="width: 85%" border>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                联系方式
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.name || '未知用户' }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.telephone || '暂无联系方式' }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.reserveDate">{{
                                            props.row.reserveDate }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务地点
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.serviceAddress">{{
                                            props.row.serviceAddress }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                下单时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderDate }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderTime }}</el-tag>
                                    </el-descriptions-item>
                                </el-descriptions>
                            </template>
                        </el-table-column>

                        <el-table-column prop="typeId" label="服务类别">
                            <template #default="scope">
                                {{ scope.row.typeB?.serviceName || '未分类' }}-{{ scope.row.typeS?.serviceName || '未分类' }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDetail" label="订单详情" />
                        <el-table-column prop="orderDate" label="下单时间" sortable />
                        <el-table-column prop="orderState" label="订单状态">
                            <template #default>
                                <p style="color: #ffa500">待受理</p>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" fixed="right" width="120px">
                            <template #default="scope">
                                <el-popconfirm v-if="scope.row.orderState == '0'" width="180" confirm-button-text="确定" cancel-button-text="取消"
                                    :icon="InfoFilled" icon-color="#626AEF" :title="'确定要取消预约吗?'"
                                    @confirm="toCancel(scope.row)">
                                    <template #reference>
                                        <el-button size="small" type="primary">取消预约</el-button>
                                    </template>
                                </el-popconfirm>
                            </template>
                        </el-table-column>
                    </el-table>
                    <MobileServiceOrders :items="noAcceptData" @cancel="toCancel" @confirm="toConfirm" @rate="toRate" />
                </div>
            </el-card>
        </el-tab-pane>
        <el-tab-pane label="待完成">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header2" width="100%" style="text-align:left">
                        <span>订单详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="noFinishData" style="width: 100%" max-height="430">
                        <!-- 展开行内容 -->
                        <el-table-column type="expand" width="56">
                            <template #default="props">
                                <el-descriptions class="descriptions" :column="3" style="width: 85%" border>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                联系方式
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.name || '未知用户' }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.telephone || '暂无联系方式' }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.reserveDate">{{
                                            props.row.reserveDate }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务地点
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.serviceAddress">{{
                                            props.row.serviceAddress }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                下单时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderDate }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderTime }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                受理时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.orderState === 2">{{
                                            props.row.acceptDate }}</el-tag>
                                        <el-tag style="margin-right: 2px" v-if="props.row.orderState === 2"> {{
                                            props.row.acceptTime }}</el-tag>
                                    </el-descriptions-item>
                                </el-descriptions>
                            </template>
                        </el-table-column>

                        <el-table-column prop="typeId" label="服务类别" width="190">
                            <template #default="scope">
                                {{ scope.row.typeB?.serviceName || '未分类' }}/{{ scope.row.typeS?.serviceName || '未分类' }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDetail" label="订单详情" width="160" />
                        <el-table-column prop="manager" label="受理人" width="180">
                            <template #default="scope">
                                <p v-if="scope.row.manager">{{ scope.row.manager.name }} {{ scope.row.manager.telephone
                                    }}</p>
                            </template>
                        </el-table-column>
                        <el-table-column prop="fff" label="服务人员" width="170">
                            <template #default="scope">
                                {{ scope.row.name }} {{ scope.row.telephone }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDate" label="下单时间" sortable width="130" />
                        <el-table-column prop="orderState" label="订单状态">
                            <template #default>
                                <p style="color: #67c23a">待完成</p>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" fixed="right" width="120px">
                            <template #default="scope">
                                <el-popconfirm v-if="scope.row.orderState == '2'" width="200" confirm-button-text="确定" cancel-button-text="取消"
                                    :icon="InfoFilled" icon-color="#626AEF" :title="'确定要确认完成服务吗?'"
                                    @confirm="toConfirm(scope.row)">
                                    <template #reference>
                                        <el-button size="small" type="primary">确认完成</el-button>
                                    </template>
                                </el-popconfirm>
                            </template>
                        </el-table-column>
                    </el-table>
                    <MobileServiceOrders :items="noFinishData" @cancel="toCancel" @confirm="toConfirm" @rate="toRate" />
                </div>
            </el-card>
        </el-tab-pane>
        <el-tab-pane label="待评价">
            <el-card class="box-card">
                <template #header>
                    <div class="card-header2" width="100%" style="text-align:left">
                        <span>订单详情</span>
                    </div>
                </template>
                <div>
                    <el-table class="desktop-record-table" :data="noReviewData" style="width: 100%" max-height="430">
                        <!-- 展开行内容 -->
                        <el-table-column type="expand" width="56">
                            <template #default="props">
                                <el-descriptions class="descriptions" :column="3" style="width: 85%" border>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                联系方式
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.name || '未知用户' }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.user?.telephone || '暂无联系方式' }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.reserveDate">{{
                                            props.row.reserveDate }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                服务地点
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px" v-if="props.row.serviceAddress">{{
                                            props.row.serviceAddress }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                下单时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderDate }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.orderTime }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                受理时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 2px">{{ props.row.acceptDate }}</el-tag>
                                        <el-tag style="margin-right: 2px">{{ props.row.acceptTime }}</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item>
                                        <template #label>
                                            <div class="cell-item">
                                                完成时间
                                            </div>
                                        </template>
                                        <el-tag style="margin-right: 5px">{{ props.row.finishDate }}</el-tag>
                                        <el-tag style="margin-right: 5px">{{ props.row.finishTime }}</el-tag>
                                    </el-descriptions-item>
                                </el-descriptions>
                            </template>
                        </el-table-column>

                        <el-table-column prop="typeId" label="服务类别" width="190">
                            <template #default="scope">
                                {{ scope.row.typeB?.serviceName || '未分类' }}/{{ scope.row.typeS?.serviceName || '未分类' }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDetail" label="订单详情" width="160" />
                        <el-table-column prop="manager" label="受理人" width="180">
                            <template #default="scope">
                                <p v-if="scope.row.manager">{{ scope.row.manager.name }} {{ scope.row.manager.telephone
                                    }}</p>
                            </template>
                        </el-table-column>
                        <el-table-column prop="fff" label="服务人员" width="170">
                            <template #default="scope">
                                {{ scope.row.name }} {{ scope.row.telephone }}
                            </template>
                        </el-table-column>
                        <el-table-column prop="orderDate" label="下单时间" sortable width="130" />
                        <el-table-column prop="orderState" label="订单状态">
                            <template #default>
                                <p style="color: #67c23a">待评分</p>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" fixed="right" width="120px">
                            <template #default="scope">
                                <el-popover v-if="scope.row.orderState == '3'" placement="bottom" :width="100" trigger="click"
                                    v-model:visible="scope.row.popVisible">
                                    <template #reference>
                                        <el-button size="small" type="primary"
                                            @click="showPopover(scope.row)">&emsp;评分&emsp;</el-button>
                                    </template>
                                    <el-rate v-model="scope.row.rate" allow-half clearable
                                        @change="toRate(scope.row)" />
                                </el-popover>
                            </template>
                        </el-table-column>
                    </el-table>
                    <MobileServiceOrders :items="noReviewData" @cancel="toCancel" @confirm="toConfirm" @rate="toRate" />
                </div>
            </el-card>
        </el-tab-pane>
    </el-tabs>
</template>

<script setup>
import { onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { storeToRefs } from "pinia";
import { useServiceorderStore } from '../../../stores/serviceorder';
import MobileServiceOrders from './MobileServiceOrders.vue'
const serviceOrderStore = useServiceorderStore()
const { myOrder, noAcceptData, noFinishData, noReviewData } = storeToRefs(serviceOrderStore)
serviceOrderStore.loadByUid()
serviceOrderStore.getNoAccept()
serviceOrderStore.getNoFinish()
serviceOrderStore.getNoReview()
//取消订单
const toCancel = (row) => {
    serviceOrderStore.updateState(row)
}
//确认完成
const toConfirm = (row) => {
    serviceOrderStore.updateFrom.finishDate = serviceOrderStore.updateDate()
    serviceOrderStore.updateFrom.finishTime = serviceOrderStore.updateTime()
    serviceOrderStore.updateState(row)
}
const initializeOrderProperties = (orders) => {
    orders.forEach((order) => {
        order.popVisible = false
    })
}
onMounted(() => {
    initializeOrderProperties(serviceOrderStore.myOrder)
})
const showPopover = (row) => {
    row.popVisible = true
}
const toRate = (row) => {
    serviceOrderStore.updateFrom.rate = row.rate
    serviceOrderStore.updateState(row)
    row.popVisible = false
}
</script>

<style scoped>
.demo-tabs {
    height: 85vh;
}

.box-card {
    height: 100%;
}

.cell-item {
    display: flex;
    align-items: center;
}

@media (max-width: 760px) {
    .demo-tabs { height: auto; min-height: 480px; }
    .desktop-record-table { display: none; }
    .box-card { height: auto; }
    .box-card :deep(.el-card__body) { padding: 12px; }
}
</style>
