<template>
    <el-config-provider :locale="locale">
        <!-- 表格 -->
        <el-table :data="tableData" style="width: 100%" :row-class-name="tableRowClassName" height="430">
            <!-- 展开行内容 -->
            <el-table-column type="expand" width="60px" fixed>
                <template #default="props">
                    <el-descriptions class="descriptions" title="订单详情" :column="2" style="width: 75%" border>
                        <el-descriptions-item>
                            <template #label>
                                <div class="cell-item">
                                    <el-icon :style="iconStyle">
                                        <user />
                                    </el-icon>
                                    顾客信息
                                </div>
                            </template>
                            <el-tag style="margin-right: 2px">{{ props.row.user?.name || '未知用户' }}</el-tag>
                            <el-tag style="margin-right: 2px" v-if="props.row.user?.sex == '0'">男</el-tag>
                            <el-tag style="margin-right: 2px" v-if="props.row.user?.sex == '1'">女</el-tag>
                            <el-tag style="margin-right: 2px" v-if="props.row.user?.age">{{
                                props.row.user?.age }}岁</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item>
                            <template #label>
                                <div class="cell-item">
                                    <el-icon :style="iconStyle">
                                        <iphone />
                                    </el-icon>
                                    联系电话
                                </div>
                            </template>
                            <el-tag>{{ props.row.user?.telephone || '暂无' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item>
                            <template #label>
                                <div class="cell-item">
                                    <el-icon :style="iconStyle">
                                        <location />
                                    </el-icon>
                                    住址
                                </div>
                            </template>
                            <el-tag style="margin-right: 5px">{{ props.row.user?.address || '暂无' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item>
                            <template #label>
                                <div class="cell-item">
                                    <el-icon :style="iconStyle">
                                        <ChatLineSquare />
                                    </el-icon>
                                    订单详情
                                </div>
                            </template>
                            <el-tag>{{ props.row.orderDetail }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item v-if="props.row.manager">
                            <template #label>
                                <div class="cell-item">
                                    <el-icon :style="iconStyle">
                                        <user />
                                    </el-icon>
                                    受理人信息
                                </div>
                            </template>
                            <el-tag style="margin-right: 2px">{{ props.row.manager.name }}</el-tag>
                            <el-tag>{{ props.row.manager.telephone }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item v-if="props.row.name && props.row.telephone">
                            <template #label>
                                <div class="cell-item">
                                    <el-icon :style="iconStyle">
                                        <user />
                                    </el-icon>
                                    服务人员信息
                                </div>
                            </template>
                            <el-tag style="margin-right: 2px">{{ props.row.name }}</el-tag>
                            <el-tag>{{ props.row.telephone }}</el-tag>
                        </el-descriptions-item>
                    </el-descriptions>
                </template>
            </el-table-column>

            <el-table-column prop="id" label="订单编号" fixed />
            <el-table-column prop="uId" label="下单人员">
                <template #default="scope">
                    {{
                        scope.row.user?.name || '未知用户'
                    }}
                </template>
            </el-table-column>
            <el-table-column prop="manager" label="受理人">
                <template #default="scope">
                    <p v-if="scope.row.manager">{{ scope.row.manager.name }}</p>
                </template>
            </el-table-column>
            <el-table-column prop="typeId" label="服务类别" width="190">
                <template #default="scope">
                    {{ scope.row.typeB?.serviceName || '未分类' }}-{{ scope.row.typeS?.serviceName || '未分类' }}
                </template>
            </el-table-column>
            <el-table-column prop="orderDetail" label="订单详情" width="160" />
            <el-table-column prop="orderDate" label="下单时间" sortable width="130" />
            <el-table-column prop="acceptDate" label="受理日期" width="130" />
            <el-table-column prop="finishDate" label="完成日期" width="130" />
            <el-table-column prop="rate" label="客户评分" width="110">
                <template #default="scope">
                    <p v-if="scope.row.rate === 1">☆</p>
                    <p v-if="scope.row.rate === 2">☆☆</p>
                    <p v-if="scope.row.rate === 3">☆☆☆</p>
                    <p v-if="scope.row.rate === 4">☆☆☆☆</p>
                    <p v-if="scope.row.rate === 5">☆☆☆☆☆</p>
                </template>
            </el-table-column>
            <el-table-column prop="orderState" label="订单状态" :filters="[
                { text: '待受理', value: 0 },
                { text: '已取消', value: 1 },
                { text: '待完成', value: 2 },
                { text: '待评分', value: 3 },
                { text: '已完成', value: 4 },
            ]" :filter-method="filterState" filter-placement="bottom-end" width="120px">
                <template #default="scope">
                    <p v-if="scope.row.orderState == '0'" style="color: #ff0000"><el-icon :style="iconStyle">
                            <Pointer />
                        </el-icon>待受理</p>
                    <p v-if="scope.row.orderState == '1'" style="color: #778899"><el-icon :style="iconStyle">
                            <Close />
                        </el-icon>已取消</p>
                    <p v-if="scope.row.orderState == '2'" style="color: #FFA500"><el-icon :style="iconStyle">
                            <Clock />
                        </el-icon>待完成</p>
                    <p v-if="scope.row.orderState == '3'" style="color: #4169E1"><el-icon :style="iconStyle">
                            <ChatLineSquare />
                        </el-icon>待评分</p>
                    <p v-if="scope.row.orderState == '4'" style="color: #67c23a"><el-icon :style="iconStyle">
                            <Select />
                        </el-icon>已完成</p>
                </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="120px">
                <template #default="scope">
                    <el-button size="small" type="primary" :disabled="scope.row.orderState != 0 ? true : false"
                        @click="handleEdit(scope.$index, scope.row)">受理</el-button>
                </template>
            </el-table-column>

        </el-table>
        <!-- 分页 -->
        <div style="text-align: -webkit-center">
            <el-pagination v-model:current-page="currentPage" :page-size="8" :total="total"
                :background="background" layout="prev, pager, next, jumper,total" @current-change="handleCurrentChange"
                style="width: max-content;margin-top: 25px" />
        </div>
    </el-config-provider>
</template>
<script setup>
import { ref, computed } from 'vue'
import { Pointer, Clock, ChatLineSquare, Select, Iphone, Location, User, Close } from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
//语言
const language = ref('zh-cn')
const locale = computed(() => (language.value === 'zh-cn' ? zhCn : en))
//斑马条
const tableRowClassName = ({ rowIndex }) => {
    return rowIndex % 2 === 1 ? 'success-row' : ''
}
//按订单状态筛选
const filterState = (value, row) => {
    return row.orderState === value
}
//图标
const size = ref('')
const iconStyle = computed(() => {
    const marginMap = {
        large: '10px',
        default: '6px',
        small: '4px'
    }
    return {
        marginRight: marginMap[size.value] || marginMap.default
    }
})

import { storeToRefs } from "pinia";
import { useServiceorderStore } from "../../../stores/serviceorder.js";
const serviceOrderStore = useServiceorderStore()
const { currentPage, total, background, tableData } = storeToRefs(serviceOrderStore)
const handleCurrentChange = (currentPage) => {
    serviceOrderStore.currentPage = currentPage
    serviceOrderStore.init()
}
serviceOrderStore.init()
const handleEdit = (index, row) => {
    serviceOrderStore.preInfo4ServiceEdit(index, row)
}
</script>

<style scoped>
.box-card {
    width: 99%;
    height: 95%;
    margin-top: 12px;
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

.item {
    margin-bottom: 18px;
}

.el-descriptions {
    margin-top: 20px;
}

.cell-item {
    display: flex;
    align-items: center;
}

.descriptions {
    margin-top: 20px;
}
</style>

<style>
.el-table .success-row {
    --el-table-tr-bg-color: #c6e2ff
}
</style>
